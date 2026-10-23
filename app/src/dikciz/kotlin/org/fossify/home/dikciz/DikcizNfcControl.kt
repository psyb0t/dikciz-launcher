package org.fossify.home.dikciz

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.TagTechnology
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizNfcControl(private val context: Context, private val logger: DikcizLogger) {
    private val busy = AtomicBoolean()
    private val handler = Handler(Looper.getMainLooper())

    fun execute(type: String, request: JSONObject): JSONObject {
        val adapter = context.getSystemService(NfcManager::class.java)?.defaultAdapter
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "NFC hardware unavailable")
        if (!adapter.isEnabled) DikcizNativeCommands.failure("nfc_disabled", "Enable NFC in Android settings before presenting a tag")
        val activity = context as? Activity
            ?: DikcizNativeCommands.failure("capture_foreground_required", "Open Dikciz before NFC operations")
        val expectedTagId = if (request.has(KEY_TAG_ID)) tagId(request) else null
        if (!busy.compareAndSet(false, true)) DikcizNativeCommands.failure("hardware_busy", "NFC reader is already active")
        val active = AtomicBoolean(true)
        val claimed = AtomicBoolean()
        val complete = CountDownLatch(1)
        val armed = CountDownLatch(1)
        val technology = AtomicReference<TagTechnology?>()
        val failure = AtomicReference<Exception?>()
        val result = AtomicReference<JSONObject?>()
        val callback = NfcAdapter.ReaderCallback { tag ->
            if (!active.get() || expectedTagId != null && !expectedTagId.contentEquals(tag.id)) return@ReaderCallback
            if (!claimed.compareAndSet(false, true)) return@ReaderCallback
            try {
                result.set(transaction(tag, type, request, technology, active))
            } catch (exception: Exception) {
                failure.set(exception)
            } finally {
                complete.countDown()
            }
        }
        try {
            handler.post {
                try {
                    if (!active.get()) return@post
                    if (activity.isDestroyed || activity.isFinishing || !activity.hasWindowFocus()) {
                        DikcizNativeCommands.failure("capture_foreground_required", "Open and unlock Dikciz before presenting an NFC tag")
                    }
                    adapter.enableReaderMode(activity, callback, READER_FLAGS, null)
                } catch (exception: Exception) {
                    failure.set(exception)
                    complete.countDown()
                } finally {
                    armed.countDown()
                }
            }
            if (!armed.await(SETUP_TIMEOUT, TimeUnit.MILLISECONDS)) DikcizNativeCommands.failure("nfc_start_timeout", "NFC reader did not become ready")
            if (!complete.await(request.optLong(KEY_TIMEOUT, DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
                DikcizNativeCommands.failure("nfc_timeout", "No NFC transaction completed before the deadline")
            }
            failure.get()?.let { exception ->
                if (exception is DikcizAutomationRequestException) throw exception
                val code = if (exception is TagLostException) "nfc_tag_lost" else "nfc_transaction_failed"
                throw DikcizAutomationRequestException(code, "NFC transaction failed; a write may have partial effects").apply { initCause(exception) }
            }
            return result.get() ?: DikcizNativeCommands.failure("nfc_transaction_failed", "NFC produced no result")
        } finally {
            active.set(false)
            try {
                technology.getAndSet(null)?.close()
            } catch (exception: IOException) {
                logger.warn("nfc_connection_close_failed", mapOf("error_class" to exception.javaClass.simpleName))
            } finally {
                handler.post {
                    try { adapter.disableReaderMode(activity) } catch (exception: RuntimeException) {
                        logger.warn("nfc_reader_cleanup_failed", mapOf("error_class" to exception.javaClass.simpleName))
                    }
                }
                busy.set(false)
            }
        }
    }

    private fun transaction(tag: Tag, type: String, request: JSONObject, owner: AtomicReference<TagTechnology?>, active: AtomicBoolean): JSONObject {
        val metadata = JSONObject().put("tagIdBase64", Base64.getEncoder().encodeToString(tag.id))
            .put("technologies", JSONArray(tag.techList.toList()))
        if (type == TYPE_TRANSCEIVE) return raw(tag, request, owner, active, metadata)
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            if (type == TYPE_READ) return metadata.put("state", "observed").put("ndefSupported", false)
            if (!request.optBoolean(KEY_FORMAT, false)) DikcizNativeCommands.failure("nfc_ndef_unsupported", "Tag is not NDEF formatted; formatting requires explicit format=true")
            val formatter = NdefFormatable.get(tag) ?: DikcizNativeCommands.failure("nfc_ndef_unsupported", "Tag cannot be formatted as NDEF")
            connect(formatter, owner, active)
            formatter.format(message(request))
            return metadata.put("state", "written").put("formatted", true).put("verified", false)
        }
        connect(ndef, owner, active)
        metadata.put("ndefSupported", true).put("maximumNdefBytes", ndef.maxSize).put("writable", ndef.isWritable)
        if (type == TYPE_WRITE) {
            if (!ndef.isWritable) DikcizNativeCommands.failure("nfc_tag_read_only", "The tag is read-only")
            val message = message(request)
            if (message.toByteArray().size > ndef.maxSize) DikcizNativeCommands.invalid("NDEF message exceeds tag capacity")
            ndef.writeNdefMessage(message)
            val observed = ndef.ndefMessage?.toByteArray()
            return metadata.put("state", "written").put("verified", observed?.contentEquals(message.toByteArray()) ?: false)
                .put("dataBase64", observed?.let { Base64.getEncoder().encodeToString(it) } ?: JSONObject.NULL)
        }
        val bytes = ndef.ndefMessage?.toByteArray()
        if (bytes != null && bytes.size > MAXIMUM_BYTES) DikcizNativeCommands.failure("nfc_response_too_large", "NDEF message exceeds the response limit")
        return metadata.put("state", "read").put("dataBase64", bytes?.let { Base64.getEncoder().encodeToString(it) } ?: JSONObject.NULL)
    }

    private fun connect(tech: TagTechnology, owner: AtomicReference<TagTechnology?>, active: AtomicBoolean) {
        owner.set(tech)
        if (!active.get()) { tech.close(); DikcizNativeCommands.failure("nfc_timeout", "NFC request has expired") }
        tech.connect()
        if (!active.get()) { tech.close(); DikcizNativeCommands.failure("nfc_timeout", "NFC request has expired") }
    }

    private fun raw(tag: Tag, request: JSONObject, owner: AtomicReference<TagTechnology?>, active: AtomicBoolean, metadata: JSONObject): JSONObject {
        val selected: TagTechnology = when (request.getString(KEY_TECH)) {
            "IsoDep" -> IsoDep.get(tag)
            "NfcA" -> NfcA.get(tag)
            "NfcB" -> NfcB.get(tag)
            "NfcF" -> NfcF.get(tag)
            "NfcV" -> NfcV.get(tag)
            "MifareClassic" -> MifareClassic.get(tag)
            "MifareUltralight" -> MifareUltralight.get(tag)
            else -> null
        } ?: DikcizNativeCommands.failure("nfc_technology_unavailable", "Presented tag does not support the requested technology")
        connect(selected, owner, active)
        val bytes = decode(request)
        val maximum = when (selected) {
            is IsoDep -> selected.maxTransceiveLength
            is NfcA -> selected.maxTransceiveLength
            is NfcB -> selected.maxTransceiveLength
            is NfcF -> selected.maxTransceiveLength
            is NfcV -> selected.maxTransceiveLength
            is MifareClassic -> selected.maxTransceiveLength
            is MifareUltralight -> selected.maxTransceiveLength
            else -> 0
        }
        if (bytes.size > maximum) DikcizNativeCommands.invalid("Command exceeds the tag technology transceive limit")
        val ioTimeout = request.optInt(KEY_IO_TIMEOUT, DEFAULT_IO_TIMEOUT)
        val response = when (selected) {
            is IsoDep -> { selected.timeout = ioTimeout; selected.transceive(bytes) }
            is NfcA -> { selected.timeout = ioTimeout; selected.transceive(bytes) }
            is NfcB -> selected.transceive(bytes)
            is NfcF -> { selected.timeout = ioTimeout; selected.transceive(bytes) }
            is NfcV -> selected.transceive(bytes)
            is MifareClassic -> { selected.timeout = ioTimeout; selected.transceive(bytes) }
            is MifareUltralight -> { selected.timeout = ioTimeout; selected.transceive(bytes) }
            else -> DikcizNativeCommands.failure("nfc_technology_unavailable", "Unsupported NFC technology")
        }
        if (response.size > MAXIMUM_BYTES) DikcizNativeCommands.failure("nfc_response_too_large", "Tag response exceeds the result limit")
        return metadata.put("state", "completed").put(KEY_TECH, request.getString(KEY_TECH))
            .put(KEY_DATA, Base64.getEncoder().encodeToString(response)).put("bytesReceived", response.size)
    }

    companion object {
        const val TYPE_READ = "nfcRead"
        const val TYPE_WRITE = "nfcWrite"
        const val TYPE_TRANSCEIVE = "nfcTransceive"
        private const val KEY_TIMEOUT = "timeoutMilliseconds"
        private const val KEY_IO_TIMEOUT = "ioTimeoutMilliseconds"
        private const val KEY_DATA = "dataBase64"
        private const val KEY_TECH = "technology"
        private const val KEY_FORMAT = "format"
        private const val KEY_TAG_ID = "expectedTagIdBase64"
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val SETUP_TIMEOUT = 5000L
        private const val DEFAULT_IO_TIMEOUT = 5000
        private const val MAXIMUM_BYTES = 32768
        private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_BARCODE
        private val TECHNOLOGIES = setOf("IsoDep", "NfcA", "NfcB", "NfcF", "NfcV", "MifareClassic", "MifareUltralight")
        private val READER_FIELDS = mapOf(KEY_TIMEOUT to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 120000).put("default", DEFAULT_TIMEOUT), KEY_TAG_ID to JSONObject().put("type", "string").put("maxLength", 88).put("description", "Optional exact tag ID from nfcRead. Other tags are ignored until the deadline. Tag IDs are not authentication credentials."))
        private val DATA_SCHEMA = JSONObject().put("type", "string").put("maxLength", (MAXIMUM_BYTES + 2) / 3 * 4).put("description", "Base64 bytes, at most 32768 decoded bytes.")
        val DEFINITIONS = listOf(
            DikcizNativeCommands.Definition(TYPE_READ, "dikciz_nfc_read", "Wait for a presented NFC tag and read its NDEF bytes and technology inventory. Open and unlock Dikciz. No tag/hardware/timeout are explicit. Reader mode and tag connections are released.", READER_FIELDS),
            DikcizNativeCommands.Definition(TYPE_WRITE, "dikciz_nfc_write", "Wait for a tag and write a validated NDEF message. format=true explicitly permits formatting an unformatted compatible tag. Read-back verification is reported. Open/unlock Dikciz and present the intended tag; writes may have partial effects.", READER_FIELDS + mapOf(KEY_DATA to DATA_SCHEMA, KEY_FORMAT to JSONObject().put("type", "boolean").put("default", false)), setOf(KEY_DATA), readOnly = false),
            DikcizNativeCommands.Definition(TYPE_TRANSCEIVE, "dikciz_nfc_transceive", "Perform one raw command/response on a presented tag using an explicit supported technology. Device-specific bytes may modify a tag. Android technology restrictions apply. Connections close after this request.", READER_FIELDS + mapOf(
                KEY_DATA to DATA_SCHEMA, KEY_TECH to JSONObject().put("type", "string").put("enum", JSONArray(TECHNOLOGIES.toList())).put("maxLength", 32),
                KEY_IO_TIMEOUT to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 60000).put("default", DEFAULT_IO_TIMEOUT)),
                setOf(KEY_TECH, KEY_DATA), readOnly = false),
        )
        val TYPES = setOf(TYPE_READ, TYPE_WRITE, TYPE_TRANSCEIVE)

        fun validate(type: String, request: JSONObject) {
            if (request.has(KEY_TAG_ID)) tagId(request)
            if (type != TYPE_READ) decode(request)
            if (type == TYPE_WRITE) message(request)
            if (type == TYPE_TRANSCEIVE && request.getString(KEY_TECH) !in TECHNOLOGIES) DikcizNativeCommands.invalid("Unsupported NFC technology")
        }

        private fun tagId(request: JSONObject): ByteArray {
            val bytes = try { Base64.getDecoder().decode(request.getString(KEY_TAG_ID)) } catch (exception: IllegalArgumentException) {
                throw DikcizAutomationRequestException("validation_failed", "Invalid NFC tag ID").apply { initCause(exception) }
            }
            if (bytes.size > 64) DikcizNativeCommands.invalid("NFC tag ID exceeds its byte limit")
            return bytes
        }

        private fun message(request: JSONObject): NdefMessage = try {
            NdefMessage(decode(request))
        } catch (exception: android.nfc.FormatException) {
            throw DikcizAutomationRequestException("validation_failed", "Invalid NDEF message").apply { initCause(exception) }
        }

        private fun decode(request: JSONObject): ByteArray {
            val bytes = try { Base64.getDecoder().decode(request.getString(KEY_DATA)) } catch (exception: IllegalArgumentException) {
                throw DikcizAutomationRequestException("validation_failed", "Invalid NFC base64 data").apply { initCause(exception) }
            }
            if (bytes.size > MAXIMUM_BYTES) DikcizNativeCommands.invalid("NFC bytes exceed the request limit")
            return bytes
        }
    }
}
