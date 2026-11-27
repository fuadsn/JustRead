package app.reader

import android.icu.text.BreakIterator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private data class Passage(val start: Locator, val end: Int, val words: Int)

private fun passages(book: EpubBook): List<Passage> = buildList {
    val locale = Locale.forLanguageTag(book.language)
    val sentences = BreakIterator.getSentenceInstance(locale)
    val words = BreakIterator.getWordInstance(locale)
    for ((chapter, part) in book.chapters.withIndex()) {
        for ((block, source) in part.blocks.withIndex()) {
            if (source !is Block.Text || source.kind == Block.Kind.Heading) continue
            val text = source.text.text
            sentences.setText(text)
            words.setText(text)
            var from = sentences.first()
            var until = sentences.next()
            while (until != BreakIterator.DONE) {
                // Use ICU word boundaries so punctuation stays attached and scripts without spaces work.
                val starts = ArrayList<Int>()
                var pos = from
                var next = words.following(from)
                while (next != BreakIterator.DONE && pos < until) {
                    if (words.ruleStatus >= BreakIterator.WORD_NUMBER) starts.add(pos)
                    pos = next
                    next = words.next()
                }
                var first = 0
                while (first < starts.size) {
                    val remaining = starts.size - first
                    // Balance parts of unusually long sentences so a lone word never spills onto a new card.
                    val chunks = (remaining + 27) / 28
                    val count = (remaining + chunks - 1) / chunks
                    var stop = first + count
                    if (stop < starts.size) {
                        val clause = (stop downTo first + maxOf(4, count - 5)).firstOrNull { boundary ->
                            text.substring(starts[boundary - 1], starts[boundary]).trimEnd().lastOrNull() in clauseMarks
                        }
                        if (clause != null) stop = clause
                    }
                    val start = if (first == 0) from else starts[first]
                    add(Passage(Locator(chapter, block, start), starts.getOrElse(stop) { until }, stop - first))
                    first = stop
                }
                from = until
                until = sentences.next()
            }
        }
    }
}

private val clauseMarks = setOf(',', ';', ':', '—', '，', '；', '：')

@Composable
internal fun SentenceReader(
    book: EpubBook, locator: Locator, settings: ReaderSettings, colors: ThemeColors,
    onMove: (Locator) -> Unit, onNormal: () -> Unit, onLibrary: () -> Unit,
) {
    val prepared by produceState<List<Passage>?>(null, book) {
        value = withContext(Dispatchers.Default) { passages(book) }
    }
    val cards = prepared
    // Find the card containing the saved character or the first readable passage after it.
    val index = remember(cards, locator) {
        if (cards.isNullOrEmpty()) 0 else {
            val result = cards.binarySearch { card ->
                when {
                    card.start.chapter != locator.chapter -> card.start.chapter.compareTo(locator.chapter)
                    card.start.block != locator.block -> card.start.block.compareTo(locator.block)
                    card.end <= locator.char -> -1
                    card.start.char > locator.char -> 1
                    else -> 0
                }
            }
            (if (result >= 0) result else -result - 1).coerceAtMost(cards.lastIndex)
        }
    }
    val card = cards?.getOrNull(index)
    val chapter = card?.let { book.chapters[it.start.chapter] }
    val text = remember(card, book) {
        if (card == null || chapter == null) "" else {
            val block = chapter.blocks[card.start.block] as Block.Text
            block.text.text.substring(card.start.char, card.end).trim()
        }
    }
    var playing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var paceOpen by remember { mutableStateOf(false) }
    var wpm by remember { mutableIntStateOf(SettingsStore.get("practiceWpm")?.toIntOrNull()?.coerceIn(100, 600) ?: 250) }
    val move by rememberUpdatedState(onMove)
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) playing = false }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(card, playing, wpm) {
        if (playing && card != null) {
            delay((card.words * 60_000L / wpm).coerceAtLeast(750L))
            if (index < cards.lastIndex) move(cards[index + 1].start)
            else { playing = false; finished = true }
        }
    }
    val jr = LocalJr.current
    Column(Modifier.fillMaxSize().background(colors.page).windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(JrIcons.Back, "Back to library", colors.text, onClick = onLibrary)
            Text(chapter?.title?.ifBlank { book.title } ?: book.title, Modifier.weight(1f),
                color = colors.secondary, style = JrType.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onNormal) { Text("Normal reading", color = colors.link) }
        }
        Box(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center) {
            key(card) {
                val size = (settings.fontSizePx * 1.45f).sp
                Text(if (cards == null) "Preparing passages…" else text.ifEmpty { "This book has no readable passages." },
                    modifier = Modifier.verticalScroll(rememberScrollState()), textAlign = TextAlign.Center,
                    color = colors.text, fontFamily = settings.font.familyOrDefault, fontSize = size, lineHeight = size * 1.4f,
                    fontWeight = if (settings.effectiveBold) FontWeight.Bold else FontWeight.Normal)
            }
        }
        if (card != null) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = index > 0, onClick = {
                    playing = false; finished = false; move(cards[index - 1].start)
                }) { Text("Previous", color = if (index > 0) colors.link else colors.secondary.copy(alpha = 0.5f)) }
                Button(enabled = !finished, onClick = { playing = !playing },
                    colors = ButtonDefaults.buttonColors(containerColor = jr.primary, contentColor = jr.onPrimary)) {
                    Text(if (finished) "Finished" else if (playing) "Pause" else "Play")
                }
                TextButton(enabled = index < cards.lastIndex, onClick = {
                    playing = false; move(cards[index + 1].start)
                }) { Text("Next", color = if (index < cards.lastIndex) colors.link else colors.secondary.copy(alpha = 0.5f)) }
                TextButton(onClick = { playing = false; paceOpen = true }) { Text("$wpm WPM", color = colors.link) }
            }
        }
    }
    if (paceOpen) AlertDialog(onDismissRequest = { paceOpen = false }, title = { Text("Reading pace") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                TextButton(enabled = wpm > 100, onClick = { wpm = (wpm - 25).coerceAtLeast(100); SettingsStore.put("practiceWpm", wpm.toString()) }) { Text("Slower") }
                Text("$wpm WPM")
                TextButton(enabled = wpm < 600, onClick = { wpm = (wpm + 25).coerceAtMost(600); SettingsStore.put("practiceWpm", wpm.toString()) }) { Text("Faster") }
            }
        }, confirmButton = { TextButton(onClick = { paceOpen = false }) { Text("Done") } })
}
