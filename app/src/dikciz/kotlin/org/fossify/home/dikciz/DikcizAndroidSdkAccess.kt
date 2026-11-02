package org.fossify.home.dikciz

import android.app.Application
import android.net.Uri
import java.io.File

/** The SDK bridge cannot bypass the file adapters by accepting a path through another public API. */
internal class DikcizAndroidSdkAccess(context: Application) {
    private val privateRoot = context.dataDir.canonicalFile.toPath()

    fun validate(value: Any?, readOnly: Boolean, depth: Int = 0) {
        if (depth > MAXIMUM_DEPTH) DikcizNativeCommands.invalid("SDK argument graph is too deep")
        when (value) {
            is CharSequence -> validateText(value.toString(), readOnly)
            is Uri -> validateText(value.toString(), readOnly)
            is File -> validatePath(value, readOnly)
            is Array<*> -> value.forEach { validate(it, readOnly, depth + 1) }
        }
    }

    private fun validateText(text: String, readOnly: Boolean) {
        var decoded = text
        repeat(MAXIMUM_URI_DECODING) {
            val next = Uri.decode(decoded)
            if (next == decoded) return@repeat
            decoded = next
        }
        if (decoded.contains(DikcizRemoteAccessAuth.RECORD_FILE_NAME)) denied()
        if (decoded.startsWith("/")) validatePath(File(decoded), readOnly)
        val uri = Uri.parse(decoded)
        if (uri.scheme == "file") uri.path?.let { validatePath(File(it), readOnly) }
    }

    private fun validatePath(file: File, readOnly: Boolean) {
        val canonical = file.canonicalFile
        val path = canonical.toPath()
        if (DikcizFileCommands.isCredentialPath(canonical) || path.startsWith(privateRoot)) denied()
        if (!readOnly && (DikcizFileCommands.containsCredentials(canonical) || privateRoot.startsWith(path))) denied()
    }

    private fun denied(): Nothing = DikcizNativeCommands.failure(
        "android_api_protected_path",
        "Protected credential paths and private launcher storage are unavailable through the SDK bridge",
    )

    companion object {
        private const val MAXIMUM_DEPTH = 8
        private const val MAXIMUM_URI_DECODING = 8
    }
}
