package app.reader

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A book on the shelf: only what the library shows. [storeId] is its saved copy on disk; the built-in
 * sample has none and keeps its (small) [bytes] instead. The chapters are parsed only while the book
 * is open, by [load].
 */
class ShelfBook(val title: String, val author: String, val cover: ImageBitmap?, val storeId: String?, private val bytes: ByteArray? = null) {
    val key: String get() = "$title|$author"

    /** Parses the whole book. Slow and memory-hungry: call it off the main thread, and only for the open book. */
    fun load(): EpubBook {
        val files = bytes?.let { unzipEntries(it) }
            ?: storeId?.let { BookStore.file(it) }?.let { unzipFile(it) }
            ?: throw EpubException("This book's file is missing.")
        return EpubParser.parse(files, title)
    }
}

/** A message for a book that couldn't be read. */
fun openError(e: Throwable, name: String): String =
    if (e is EpubException) e.message.orEmpty() else "Couldn't open $name: ${e.message ?: e::class.simpleName}"

/** Saved reading spot: chapter, paragraph and character. */
data class Locator(val chapter: Int, val block: Int, val char: Int) {
    fun encode() = "$chapter,$block,$char"
    companion object {
        fun decode(s: String?): Locator? = s?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 3 }?.let { Locator(it[0], it[1], it[2]) }
    }
}

data class Bookmark(val locator: Locator, val created: Long)

enum class SortBy(val label: String) { Recent("Recently read"), Title("Title"), Author("Author"), Added("Date added"), Progress("Progress") }
enum class ShelfView { Grid, List }

class AppController(private val restoreLastBook: Boolean = true) {
    val shelf = mutableStateListOf<ShelfBook>()
    val back = BackStack()
    private var readingState = mutableStateOf<ShelfBook?>(null)

    /** The open book; remembered so the next launch goes straight back to it. */
    var reading: ShelfBook?
        get() = readingState.value
        set(v) {
            readingState.value = v
            SettingsStore.put("lastBook", v?.key ?: "")
            if (v != null) { SettingsStore.put("seen:" + v.key, nowMillis().toString()); touch() }
        }
    var settings by mutableStateOf(ReaderSettings.decode(SettingsStore.get("settings")))
        private set
    var error by mutableStateOf<String?>(null)
    var requestOpenFile: (() -> Unit)? = null

    /** Bumped whenever saved per-book data changes, so the library recomposes. */
    var metaVersion by mutableIntStateOf(0)
        private set
    private fun touch() { metaVersion++ }

    var sortBy by mutableStateOf(SettingsStore.get("sort")?.let { s -> SortBy.entries.firstOrNull { it.name == s } } ?: SortBy.Recent)
        private set
    var view by mutableStateOf(SettingsStore.get("view")?.let { s -> ShelfView.entries.firstOrNull { it.name == s } } ?: ShelfView.Grid)
        private set
    fun updateSort(s: SortBy) { sortBy = s; SettingsStore.put("sort", s.name) }
    fun updateView(v: ShelfView) { view = v; SettingsStore.put("view", v.name) }

    /** False until [loadLibrary] has listed the saved books. */
    var libraryLoaded by mutableStateOf(false)
        private set

    /** A book from the last session is about to be reopened (so the library isn't flashed first). */
    val restoring: Boolean get() = !libraryLoaded && restoreLastBook && reading == null && !SettingsStore.get("lastBook").isNullOrEmpty()

    init {
        runCatching {
            val bytes = SampleBook.build()
            val files = unzipEntries(bytes)
            val sum = EpubParser.summary({ files[it] }, files.keys, SampleBook.TITLE)
            place(ShelfBook(sum.title, sum.author, sum.coverPath?.let { files[it] }?.let { decodePicture(it, COVER_PX) }, null, bytes))
        }
        if (restoreLastBook) restoreLast()
    }

    private fun restoreLast() {
        if (reading != null) return
        val last = SettingsStore.get("lastBook")
        shelf.firstOrNull { it.key == last }?.let { readingState.value = it }
    }

    /**
     * Lists the saved books off the main thread, reading only each one's package file and cover,
     * then reopens the book from last time.
     */
    suspend fun loadLibrary() {
        val found = withContext(Dispatchers.IO) {
            BookStore.files().mapNotNull { f ->
                val id = f.nameWithoutExtension
                runCatching { summarise(f, id, id) }.getOrElse { BookStore.remove(id); null }
            }
        }
        // least recently used first, so the newest ends up on top; skip any opened meanwhile
        for (b in found) if (shelf.none { it.key == b.key }) place(b)
        libraryLoaded = true
        if (restoreLastBook) restoreLast()
    }

    /** A saved book's shelf entry, from its package file and cover alone. */
    private fun summarise(file: java.io.File, name: String, id: String): ShelfBook = withZipFile(file) { read, names ->
        val sum = EpubParser.summary(read, names, name.substringBeforeLast('.'))
        ShelfBook(sum.title, sum.author, sum.coverPath?.let(read)?.let { decodePicture(it, COVER_PX) }, id)
    }

    private fun place(entry: ShelfBook) {
        shelf.removeAll { it.key == entry.key }
        shelf.add(0, entry)
        if (SettingsStore.get("added:" + entry.key) == null) SettingsStore.put("added:" + entry.key, nowMillis().toString())
        touch()
    }

    fun updateSettings(s: ReaderSettings) {
        settings = s
        SettingsStore.put("settings", s.encode())
    }

    /** Saves a copy of a book the person picked, adds it to the shelf and opens it. */
    suspend fun open(bytes: ByteArray, name: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val id = storeId(bytes)
                BookStore.save(id, bytes)
                val file = BookStore.file(id) ?: throw EpubException("Couldn't save $name.")
                runCatching { summarise(file, name, id) }.getOrElse { BookStore.remove(id); throw it }
            }
        }.onSuccess { place(it); reading = it }.onFailure { error = openError(it, name) }
    }

    fun remove(b: ShelfBook) {
        shelf.remove(b)
        b.storeId?.let { BookStore.remove(it) }
        if (reading == b) reading = null
        touch()
    }

    /** Stable file name for a book's bytes (size + content hash). */
    private fun storeId(bytes: ByteArray): String =
        "book-" + bytes.size.toString(16) + "-" + (bytes.contentHashCode().toLong() and 0xffffffffL).toString(16)

    // ---- per-book data

    fun position(b: ShelfBook): Locator? = Locator.decode(SettingsStore.get("pos:" + b.key))
    fun progress(b: ShelfBook): Float? = SettingsStore.get("prog:" + b.key)?.toFloatOrNull()
    fun lastOpened(b: ShelfBook): Long = SettingsStore.get("seen:" + b.key)?.toLongOrNull() ?: 0L
    fun added(b: ShelfBook): Long = SettingsStore.get("added:" + b.key)?.toLongOrNull() ?: 0L
    fun chapterTitle(b: ShelfBook): String? = SettingsStore.get("chap:" + b.key)?.takeIf { it.isNotBlank() }
    fun lastPage(b: ShelfBook): Int? = SettingsStore.get("page:" + b.key)?.toIntOrNull()
    fun finished(b: ShelfBook): Boolean = (progress(b) ?: 0f) >= 0.999f

    fun savePosition(b: ShelfBook, l: Locator, page: Int, pageCount: Int, chapter: String) {
        SettingsStore.put("pos:" + b.key, l.encode())
        val fraction = if (pageCount <= 1) 1f else page / (pageCount - 1).toFloat()
        SettingsStore.put("prog:" + b.key, fraction.toString())
        SettingsStore.put("page:" + b.key, (page + 1).toString())
        SettingsStore.put("chap:" + b.key, chapter)
        touch()
    }

    fun markFinished(b: ShelfBook, done: Boolean) {
        SettingsStore.put("prog:" + b.key, if (done) "1.0" else "0.0")
        if (!done) SettingsStore.put("pos:" + b.key, Locator(0, 0, 0).encode())
        touch()
    }

    fun bookmarks(b: ShelfBook): List<Bookmark> =
        SettingsStore.get("bm:" + b.key)?.split(';')?.mapNotNull { e ->
            val p = e.split('@'); val l = Locator.decode(p.getOrNull(0)) ?: return@mapNotNull null
            Bookmark(l, p.getOrNull(1)?.toLongOrNull() ?: 0L)
        } ?: emptyList()

    private fun saveBookmarks(b: ShelfBook, list: List<Bookmark>) {
        SettingsStore.put("bm:" + b.key, list.joinToString(";") { it.locator.encode() + "@" + it.created })
        touch()
    }

    fun addBookmark(b: ShelfBook, l: Locator) {
        val list = bookmarks(b)
        if (list.none { it.locator == l }) saveBookmarks(b, (list + Bookmark(l, nowMillis())).sortedWith(compareBy({ it.locator.chapter }, { it.locator.block }, { it.locator.char })))
    }

    fun removeBookmarks(b: ShelfBook, which: (Bookmark) -> Boolean) = saveBookmarks(b, bookmarks(b).filterNot(which))

    // ---- highlights and notes

    /** Bumped when any book's highlights change, so pages showing them are drawn again. */
    var highlightsVersion by mutableIntStateOf(0)
        private set

    fun highlights(b: ShelfBook): List<Highlight> = Highlight.decodeAll(SettingsStore.get("hl:" + b.key))

    private fun saveHighlights(b: ShelfBook, list: List<Highlight>) {
        SettingsStore.put("hl:" + b.key, Highlight.encodeAll(list.sortedWith(Highlight.BookOrder)))
        highlightsVersion++
    }

    /** Adds [h], or replaces the highlight with the same id. */
    fun putHighlight(b: ShelfBook, h: Highlight) = saveHighlights(b, highlights(b).filterNot { it.id == h.id } + h)

    fun removeHighlight(b: ShelfBook, id: Long) = saveHighlights(b, highlights(b).filterNot { it.id == id })

    // ---- reading goal

    var goalMinutes by mutableIntStateOf(SettingsStore.get("goal")?.toIntOrNull() ?: 20)
        private set
    fun updateGoal(m: Int) { goalMinutes = m; SettingsStore.put("goal", m.toString()); touch() }

    private fun secondsOn(day: Long): Int = SettingsStore.get("read:$day")?.toIntOrNull() ?: 0
    fun secondsToday(): Int = secondsOn(localEpochDay())

    fun addReadingSeconds(s: Int) {
        val day = localEpochDay()
        SettingsStore.put("read:$day", (secondsOn(day) + s).toString())
        touch()
    }

    /** Days in a row the goal was met, counting today only once it is met. */
    fun streak(): Int {
        val goal = goalMinutes * 60
        var day = localEpochDay()
        if (secondsOn(day) < goal) day--
        var n = 0
        while (n < 3650 && secondsOn(day) >= goal) { n++; day-- }
        return n
    }

    companion object {
        /** Covers are decoded no larger than this (px); the library shows them at most ~200 dp tall. */
        const val COVER_PX = 900
    }

    fun sortedShelf(): List<ShelfBook> = when (sortBy) {
        SortBy.Recent -> shelf.sortedWith(compareByDescending<ShelfBook> { lastOpened(it) }.thenByDescending { added(it) })
        SortBy.Title -> shelf.sortedBy { it.title.removePrefix("The ").lowercase() }
        SortBy.Author -> shelf.sortedBy { it.author.substringAfterLast(' ').lowercase() }
        SortBy.Added -> shelf.sortedByDescending { added(it) }
        SortBy.Progress -> shelf.sortedByDescending { progress(it) ?: -1f }
    }
}

@Composable
fun App(controller: AppController = remember { AppController() }, systemDark: Boolean = false) {
    rememberBundledFonts()
    val settings = controller.settings
    val ambient = if (settings.appearance == Appearance.Ambient) rememberAmbientDark() else null
    val uiDark = settings.appearanceDark(systemDark, ambient)
    val reading = controller.reading

    LaunchedEffect(reading != null, settings.brightness) {
        Hooks.setBrightness(if (reading != null && settings.brightness >= 0f) settings.brightness else null)
    }
    LaunchedEffect(settings.appearance) {
        Hooks.setNightMode(when (settings.appearance) { Appearance.Light -> false; Appearance.Dark -> true; else -> null })
    }
    LaunchedEffect(reading != null, settings.orientation) {
        Hooks.setOrientation(if (reading != null) settings.orientation else ReadingOrientation.System)
    }

    JustReadTheme(uiDark) {
        CompositionLocalProvider(LocalBackStack provides controller.back) {
            if (reading != null) {
                BackAction { controller.reading = null }
                // the parsed book lives only while it is open: leaving the reader drops it
                val book by produceState<EpubBook?>(null, reading) {
                    value = null
                    runCatching { withContext(Dispatchers.Default) { reading.load() } }
                        .onSuccess { value = it }
                        .onFailure { controller.error = openError(it, reading.title); controller.reading = null }
                }
                val b = book
                if (b == null) OpeningScreen(reading, controller.lastPage(reading))
                else ReaderScreen(controller, reading, b, uiDark, onClose = { controller.reading = null })
            } else if (controller.restoring) {
                Box(Modifier.fillMaxSize().background(LocalJr.current.background))
            } else {
                Library(controller)
            }
            controller.error?.let { msg ->
                val jr = LocalJr.current
                AlertDialog(
                    onDismissRequest = { controller.error = null },
                    confirmButton = { TextButton(onClick = { controller.error = null }) { Text("OK", color = jr.primary) } },
                    title = { Text("Can’t open this book", style = JrType.headline, color = jr.onBackground) },
                    text = { Text(msg, style = JrType.body, color = jr.onVariant) },
                    containerColor = jr.sheet,
                )
            }
        }
    }
}
