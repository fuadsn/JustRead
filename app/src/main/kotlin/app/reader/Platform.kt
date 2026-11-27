package app.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Reads every file of a ZIP (EPUB) archive into memory, keyed by its path inside the archive. */
fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
        while (true) {
            val e = zin.nextEntry ?: break
            if (!e.isDirectory) out[e.name.trimStart('/')] = zin.readBytes()
        }
    }
    return out
}

/**
 * Opens a ZIP (EPUB) on disk and hands [block] a reader for single entries plus the entry names,
 * so only the entries asked for are ever decompressed.
 */
fun <T> withZipFile(file: java.io.File, block: (read: (String) -> ByteArray?, names: List<String>) -> T): T =
    ZipFile(file).use { zip ->
        val names = zip.entries().asSequence().filter { !it.isDirectory }.map { it.name.trimStart('/') }.toList()
        block({ name -> (zip.getEntry(name) ?: zip.getEntry("/$name"))?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }, names)
    }

/** Every file of a ZIP on disk, keyed by its path inside the archive. */
fun unzipFile(file: java.io.File): Map<String, ByteArray> =
    runCatching { withZipFile(file) { read, names -> names.associateWith { read(it) ?: ByteArray(0) } } }
        .getOrElse { unzipEntries(file.readBytes()) } // a few archives only open as a stream

/** Writes a ZIP archive; the first entry is stored uncompressed (EPUB needs that for "mimetype"). */
fun zipEntries(entries: List<Pair<String, ByteArray>>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zos ->
        entries.forEachIndexed { i, (name, data) ->
            val e = ZipEntry(name)
            if (i == 0) {
                e.method = ZipEntry.STORED
                e.size = data.size.toLong(); e.compressedSize = data.size.toLong()
                e.crc = CRC32().apply { update(data) }.value
            }
            zos.putNextEntry(e); zos.write(data); zos.closeEntry()
        }
    }
    return bos.toByteArray()
}

/**
 * Decodes a PNG/JPEG/GIF/WebP picture, or null when it can't be read. With [maxSide] the picture is
 * subsampled (by powers of two) so neither side is much larger than that: a 3000 px cover shown at
 * 150 dp would otherwise cost tens of MB and a slow texture upload.
 */
fun decodePicture(bytes: ByteArray, maxSide: Int = 0): ImageBitmap? = runCatching {
    val opts = BitmapFactory.Options()
    if (maxSide > 0) {
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
        opts.inJustDecodeBounds = false
        opts.inSampleSize = sample
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.also { it.prepareToDraw() }?.asImageBitmap()
}.getOrNull()

/** A picture's size in pixels, read from its header without decoding it; null when it can't be read. */
fun pictureSize(bytes: ByteArray): androidx.compose.ui.unit.IntSize? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    return if (opts.outWidth > 0 && opts.outHeight > 0) androidx.compose.ui.unit.IntSize(opts.outWidth, opts.outHeight) else null
}

/**
 * The book's pictures, decoded only when a page draws them and kept in a cache of at most
 * [maxBytes] of pixels, so an illustrated book never holds all of its pictures at once.
 */
class PictureCache(private val files: Map<String, ByteArray>, maxBytes: Int = 48 shl 20, private val maxSide: Int = 1600) {
    private val missing = HashSet<String>()
    private val cache = object : android.util.LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().allocationByteCount
    }
    operator fun get(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        if (path in missing) return null
        val img = files[path]?.let { decodePicture(it, maxSide) }
        if (img == null) missing += path else cache.put(path, img)
        return img
    }
    fun clear() = cache.evictAll()
}

/** Small persistent key/value store for reader settings. */
object SettingsStore {
    private var prefs: SharedPreferences? = null
    fun init(context: Context) { prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE) }
    fun get(key: String): String? = prefs?.getString(key, null)
    fun put(key: String, value: String) { prefs?.edit()?.putString(key, value)?.apply() }
}

/** Keeps the system back gesture off the lower part of the page edges, where page curls usually start (Android). */
fun androidx.compose.ui.Modifier.reserveEdgesForPageTurns(): androidx.compose.ui.Modifier =
    this.systemGestureExclusion { coords: androidx.compose.ui.layout.LayoutCoordinates ->
        // Android honours at most 200 dp per edge; take the band above the navigation area
        val dp = android.content.res.Resources.getSystem().displayMetrics.density
        val h = coords.size.height.toFloat(); val w = coords.size.width.toFloat()
        val band = (h * 0.3f).coerceAtMost(200f * dp)
        androidx.compose.ui.geometry.Rect(0f, h - band - 40f * dp, w, h - 40f * dp)
    }

/** Keeps copies of added EPUB files so the library survives restarts. */
object BookStore {
    private var dir: java.io.File? = null
    fun init(context: Context) { dir = java.io.File(context.filesDir, "books").apply { mkdirs() } }
    fun save(id: String, bytes: ByteArray) {
        val d = dir ?: return
        val f = java.io.File(d, "$id.epub")
        if (f.exists()) f.setLastModified(System.currentTimeMillis()) else {
            val tmp = java.io.File(d, "$id.tmp"); tmp.writeBytes(bytes); tmp.renameTo(f)
        }
    }
    /** The saved books, least recently used first. Nothing is read from them. */
    fun files(): List<java.io.File> =
        dir?.listFiles { f -> f.name.endsWith(".epub") }?.sortedBy { it.lastModified() } ?: emptyList()
    fun file(id: String): java.io.File? = dir?.let { java.io.File(it, "$id.epub") }?.takeIf { it.exists() }
    fun remove(id: String) { dir?.let { java.io.File(it, "$id.epub").delete() } }
}

/** The AGSL curl shader, made once; a BitmapShader per page picture. */
@SuppressLint("NewApi")
private object CurlShader {
    val shader: RuntimeShader? by lazy { runCatching { RuntimeShader(CURL_SHADER) }.getOrNull() }
    val paint = android.graphics.Paint()
    // the last two pictures' shaders (front and back of a two-page turn)
    private val cache = ArrayList<Pair<ImageBitmap, BitmapShader>>(2)

    fun pageShader(img: ImageBitmap): BitmapShader {
        cache.firstOrNull { it.first === img }?.let { return it.second }
        val s = BitmapShader(img.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        if (cache.size >= 2) cache.removeAt(0)
        cache += img to s
        return s
    }
}

/**
 * Draws [page] curled by [curl] with the curl shader. Returns false below Android 13, where
 * there is no runtime shader, so the caller draws the strip fallback instead.
 */
@SuppressLint("NewApi")
fun DrawScope.drawCurlWithShader(
    page: ImageBitmap, curl: Curl, paper: Color, shade: Color, shadowStrength: Float,
    back: ImageBitmap? = null, dir: Float = 0f,
): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    val sh = CurlShader.shader ?: return false
    sh.setInputShader("page", CurlShader.pageShader(page))
    sh.setInputShader("back", CurlShader.pageShader(back ?: page))
    sh.setFloatUniform("dir", dir)
    sh.setFloatUniform("size", page.width.toFloat(), page.height.toFloat())
    sh.setFloatUniform("n", curl.nx, curl.ny)
    sh.setFloatUniform("pos", curl.position)
    sh.setFloatUniform("r", curl.radius.coerceAtLeast(0.5f))
    sh.setFloatUniform("paper", paper.red, paper.green, paper.blue)
    sh.setFloatUniform("shade", shade.red, shade.green, shade.blue)
    sh.setFloatUniform("shadowK", shadowStrength)
    sh.setFloatUniform("dp", shadowUnit(page.width.toFloat()))
    val paint = CurlShader.paint
    paint.shader = sh
    drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    return true
}

/** Milliseconds since the epoch. */
fun nowMillis(): Long = System.currentTimeMillis()

/** Today's date in the device's time zone, as days since 1970-01-01. */
fun localEpochDay(): Long {
    val now = System.currentTimeMillis()
    val offset = java.util.TimeZone.getDefault().getOffset(now)
    return Math.floorDiv(now + offset, 86_400_000L)
}

/** Whether the room is dark, from the light sensor; null when there is no sensor. */
@Composable
fun rememberAmbientDark(): Boolean? {
    val context = LocalContext.current
    var dark by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val lux = e.values.firstOrNull() ?: return
                // a little hysteresis so the page doesn't flicker around the threshold
                dark = when (dark) {
                    true -> lux < 25f
                    false -> lux < 10f
                    null -> lux < 15f
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm?.unregisterListener(listener) }
    }
    return dark
}

/** Things only the app shell can do (set by MainActivity). */
object Hooks {
    /** Window brightness 0…1, or null to follow the system. */
    var setBrightness: (Float?) -> Unit = {}
    var setOrientation: (ReadingOrientation) -> Unit = {}
    var share: (String) -> Unit = {}
    /** Tells the system the app's light/dark choice (null = follow the system), so the next launch screen matches. */
    var setNightMode: (Boolean?) -> Unit = {}
}
