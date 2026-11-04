package org.fossify.home.dikciz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizVision {
    const val READ = "imageRecognize"
    const val DOCUMENT = "documentScan"
    private const val MAX_FILE = 33554432L
    private const val MAX_SOURCE_PIXELS = 64000000L
    private const val MAX_DECODED_PIXELS = 8388608L
    private const val MAX_RESPONSE = 524288
    private const val MAX_DOCUMENT_PIXELS = 16777216L
    private const val DEFAULT_TIMEOUT = 30000L
    private val work = Semaphore(2)
    private val direct = Executor { it.run() }
    fun execute(context: Context, type: String, request: JSONObject): JSONObject =
        if (type == READ) recognize(request) else document(context, request)

    private fun image(path: String, maximum: Int): Bitmap {
        val file = DikcizFileCommands.file(JSONObject().put("path", path))
        if (!file.isFile || file.length() !in 1..MAX_FILE) DikcizNativeCommands.invalid("Image must be a readable file of at most 32 MiB")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS) {
            DikcizNativeCommands.invalid("Unsupported image or source image exceeds 64 million pixels")
        }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maximum ||
            (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > MAX_DECODED_PIXELS) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: DikcizNativeCommands.invalid("Image could not be decoded")
        return bitmap
    }
    private fun recognize(request: JSONObject): JSONObject {
        val mode = request.optString("mode", "both")
        if (mode !in setOf("text", "barcodes", "both")) DikcizNativeCommands.invalid("Choose text, barcodes or both")
        val rotation = request.optInt("rotationDegrees", 0)
        if (rotation !in setOf(0, 90, 180, 270)) DikcizNativeCommands.invalid("Rotation must be 0, 90, 180 or 270")
        if (!work.tryAcquire()) DikcizNativeCommands.failure("vision_busy", "At most two image jobs can run")
        var handedOff = false
        var bitmap: Bitmap? = null
        var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
        var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
        try {
            val decoded = image(request.getString("path"), request.optInt("maxDimension", 2048))
            bitmap = decoded
            val input = InputImage.fromBitmap(decoded, rotation)
            recognizer = if (mode != "barcodes") TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null
            scanner = if (mode != "text") BarcodeScanning.getClient() else null
            val textTask = recognizer?.process(input)
            val barcodeTask = scanner?.process(input)
            val tasks = listOfNotNull<Task<*>>(textTask, barcodeTask)
            val finished = Tasks.whenAllComplete(tasks)
            val width = if (rotation == 90 || rotation == 270) decoded.height else decoded.width
            val height = if (rotation == 90 || rotation == 270) decoded.width else decoded.height
            finished.addOnCompleteListener(direct) {
                try { recognizer?.close(); scanner?.close() }
                finally { decoded.recycle(); work.release() }
            }
            handedOff = true
            try { Tasks.await(finished, request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS) }
            catch (exception: java.util.concurrent.TimeoutException) {
                DikcizNativeCommands.failure("vision_timeout", "Image processing exceeded the deadline; its bounded worker releases resources when the model finishes")
            }
            if (tasks.any { !it.isSuccessful }) DikcizNativeCommands.failure("vision_failed", "The on-device recognition model rejected the image")
            val maximum = request.optInt("maximumResults", 200)
            val maxText = request.optInt("maximumTextCharacters", 65536)
            val result = JSONObject().put("processedWidth", width).put("processedHeight", height)
                .put("rotationDegrees", rotation).put("imageUploaded", false).put("textScript", "Latin")
            textTask?.result?.let { text ->
                if (text.text.length > maxText) DikcizNativeCommands.failure("vision_text_too_large", "Recognized text exceeds maximumTextCharacters")
                result.put("text", text.text).put("textBlocks", JSONArray(text.textBlocks.take(maximum).map { block ->
                    JSONObject().put("text", block.text).put("bounds", bounds(block.boundingBox))
                        .put("language", block.recognizedLanguage)
                })).put("textBlockLimitReached", text.textBlocks.size > maximum)
            }
            barcodeTask?.result?.let { codes ->
                result.put("barcodes", JSONArray(codes.take(maximum).map { code ->
                    JSONObject().put("rawValue", code.rawValue ?: JSONObject.NULL)
                        .put("format", code.format).put("valueType", code.valueType).put("bounds", bounds(code.boundingBox))
                })).put("barcodeLimitReached", codes.size > maximum)
            }
            if (result.toString().toByteArray(Charsets.UTF_8).size > MAX_RESPONSE) DikcizNativeCommands.failure("vision_response_too_large", "Recognition result exceeds 512 KiB")
            return result.put("state", "recognized")
        } finally {
            if (!handedOff) {
                try { recognizer?.close(); scanner?.close() }
                finally { bitmap?.recycle(); work.release() }
            }
        }
    }
    private fun document(context: Context, request: JSONObject): JSONObject {
        if (!work.tryAcquire()) DikcizNativeCommands.failure("vision_busy", "At most two image jobs can run")
        return try { writeDocument(context, request) } finally { work.release() }
    }
    private fun writeDocument(context: Context, request: JSONObject): JSONObject {
        val pages = request.getJSONArray("pages")
        val root = File(context.getExternalFilesDir("captures") ?: context.filesDir, "documents/" + UUID.randomUUID())
        if (!root.mkdirs()) DikcizNativeCommands.failure("document_write_failed", "Could not create document output")
        val pdf = PdfDocument()
        val images = JSONArray()
        var complete = false
        var totalPixels = 0L
        try {
            for (index in 0 until pages.length()) {
                val page = pages.getJSONObject(index)
                val input = image(page.getString("path"), request.optInt("maxDimension", 2048))
                var prepared: Bitmap? = null
                try {
                    prepared = perspective(input, page.optJSONArray("corners"))
                    totalPixels += prepared.width.toLong() * prepared.height
                    if (totalPixels > MAX_DOCUMENT_PIXELS) DikcizNativeCommands.failure("document_pixel_budget", "Document exceeds 16 million processed pixels; reduce maxDimension or split the pages")
                    if (request.optBoolean("grayscale", true)) {
                        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
                        val gray = Bitmap.createBitmap(prepared.width, prepared.height, Bitmap.Config.ARGB_8888)
                        Canvas(gray).drawBitmap(prepared, 0f, 0f, paint)
                        if (prepared !== input) prepared.recycle()
                        prepared = gray
                    }
                    val target = File(root, "page-" + (index + 1) + ".jpg")
                    target.outputStream().use { if (!prepared.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw java.io.IOException("JPEG encoding failed") }
                    val output = pdf.startPage(PdfDocument.PageInfo.Builder(prepared.width, prepared.height, index + 1).create())
                    output.canvas.drawColor(Color.WHITE)
                    output.canvas.drawBitmap(prepared, 0f, 0f, null)
                    pdf.finishPage(output)
                    images.put(JSONObject().put("path", target.absolutePath).put("width", prepared.width).put("height", prepared.height))
                } finally {
                    if (prepared != null && prepared !== input) prepared.recycle()
                    input.recycle()
                }
            }
            val target = File(root, "document.pdf")
            target.outputStream().use(pdf::writeTo)
            complete = true
            return JSONObject().put("state", "written").put("path", target.absolutePath).put("pages", images)
                .put("searchableTextLayer", false).put("imageUploaded", false)
        } finally {
            pdf.close()
            if (!complete && !root.deleteRecursively()) {
                DikcizLogger("DikcizVision", HomeConfigStore(context).logDirectory).warn("document_partial_cleanup_failed")
            }
        }
    }
    private fun perspective(input: Bitmap, corners: JSONArray?): Bitmap {
        if (corners == null) return input
        if (corners.length() != 4) DikcizNativeCommands.invalid("Provide exactly four normalized corners in top-left, top-right, bottom-right, bottom-left order")
        val source = FloatArray(8)
        for (index in 0..3) {
            val point = corners.getJSONObject(index)
            val x = point.getDouble("x"); val y = point.getDouble("y")
            if (!x.isFinite() || !y.isFinite() || x !in 0.0..1.0 || y !in 0.0..1.0) DikcizNativeCommands.invalid("Corners must be normalized to 0..1")
            source[index * 2] = (x * input.width).toFloat()
            source[index * 2 + 1] = (y * input.height).toFloat()
        }
        val cross = (0..3).map { index ->
            val a = index * 2; val b = ((index + 1) % 4) * 2; val c = ((index + 2) % 4) * 2
            (source[b] - source[a]) * (source[c + 1] - source[b + 1]) - (source[b + 1] - source[a + 1]) * (source[c] - source[b])
        }
        if (cross.any { it <= 1f }) DikcizNativeCommands.invalid("Corners must describe a nonempty clockwise convex page")
        fun distance(a: Int, b: Int) = kotlin.math.hypot(source[a*2] - source[b*2], source[a*2+1] - source[b*2+1]).toInt()
        val width = maxOf(distance(0,1), distance(3,2)).coerceAtLeast(1)
        val height = maxOf(distance(0,3), distance(1,2)).coerceAtLeast(1)
        if (width.toLong()*height > MAX_DECODED_PIXELS) DikcizNativeCommands.invalid("Corrected page exceeds the pixel bound")
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(source, 0, floatArrayOf(0f,0f,width.toFloat(),0f,width.toFloat(),height.toFloat(),0f,height.toFloat()),0,4)) {
            DikcizNativeCommands.invalid("Corners cannot define a perspective transform")
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(input, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
        }
    }
    private fun bounds(value: Rect?): Any = value?.let { JSONObject().put("left", it.left).put("top", it.top).put("right", it.right).put("bottom", it.bottom) } ?: JSONObject.NULL
    private fun text(max: Int) = JSONObject().put("type", "string").put("minLength", 1).put("maxLength", max)
    private fun integer(min: Long, max: Long, default: Long) = JSONObject().put("type", "integer").put("minimum", min).put("maximum", max).put("default", default)
    private val coordinate = JSONObject().put("type", "number").put("minimum",0).put("maximum",1)
    private val corner = JSONObject().put("type","object").put("properties",JSONObject(mapOf("x" to coordinate,"y" to coordinate)))
        .put("required",JSONArray(listOf("x","y"))).put("additionalProperties",false)
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(READ, "dikciz_image_recognize", "Read text, barcodes or both from an accessible image using bundled on-device models. Latin text recognition; barcode formats are reported as ML Kit constants. No image upload, link opening or instruction execution. Bounds refer to the processed rotated image. Unsupported/oversized inputs fail explicitly.",
            mapOf("path" to text(4096), "mode" to text(16).put("enum",JSONArray(listOf("text","barcodes","both"))),
                "rotationDegrees" to integer(0,270,0).put("enum",JSONArray(listOf(0,90,180,270))),
                "maxDimension" to integer(320,4096,2048), "maximumResults" to integer(1,1000,200),
                "maximumTextCharacters" to integer(1,262144,65536), "timeoutMilliseconds" to integer(1,120000,DEFAULT_TIMEOUT)), setOf("path")),
        DikcizNativeCommands.Definition(DOCUMENT, "dikciz_document_scan", "Create cleaned JPEG pages and an image PDF from 1..20 accessible images. Optional normalized clockwise corners correct perspective; absent corners retain the full image. This does not claim automatic edge detection or a searchable PDF text layer. Use imageRecognize on the returned pages for OCR. Output is saved in app capture storage; no image upload.",
            mapOf("pages" to JSONObject().put("type","array").put("minItems",1).put("maxItems",20).put("items",
                JSONObject().put("type","object").put("properties",JSONObject(mapOf("path" to text(4096),
                    "corners" to JSONObject().put("type","array").put("minItems",4).put("maxItems",4).put("items",corner))))
                    .put("required",JSONArray(listOf("path"))).put("additionalProperties",false)),
                "grayscale" to JSONObject().put("type","boolean").put("default",true), "maxDimension" to integer(320,4096,2048)), setOf("pages"), false),
    )
    val TYPES = DEFINITIONS.map { it.type }.toSet()
}
