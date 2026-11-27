package app.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.delay

private const val passageLimit = 120
private val wordPattern = Regex("""[^\s\p{Z}]+""")

/** Take a short practice passage from this position without moving the book's bookmark. */
internal fun practicePassage(book: EpubBook, start: Locator): List<String> = sequence {
    for (chapter in start.chapter until book.chapters.size) {
        val blocks = book.chapters[chapter].blocks
        val first = if (chapter == start.chapter) start.block else 0
        for (block in first until blocks.size) {
            val text = (blocks[block] as? Block.Text)?.text?.text ?: continue
            var offset = if (chapter == start.chapter && block == first) start.char.coerceIn(0, text.length) else 0
            // If a page break split a word, include the whole word.
            while (offset in 1 until text.length && !text[offset].isWhitespace() && !text[offset - 1].isWhitespace()) offset--
            for (word in wordPattern.findAll(text, offset)) yield(word.value)
        }
    }
}.take(passageLimit).toList()

@Composable
internal fun PracticeDialog(book: EpubBook, start: Locator, onClose: () -> Unit) {
    val words = remember(book, start) { practicePassage(book, start) }
    var index by remember(words) { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var wpm by remember { mutableIntStateOf(SettingsStore.get("practiceWpm")?.toIntOrNull()?.coerceIn(100, 600) ?: 250) }
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) running = false }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(running, wpm, words) {
        if (running) {
            while (index < words.size) {
                delay(60_000L / wpm)
                index++
            }
            running = false
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Speed practice") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                if (words.isEmpty()) Text("There’s no text to practice from this position.")
                else {
                    Text(if (index == words.size) "Passage complete" else words[index],
                        style = JrType.headline, textAlign = TextAlign.Center)
                    Text("${minOf(index + 1, words.size)} of ${words.size} words", style = JrType.caption)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = wpm > 100, onClick = { wpm -= 25; SettingsStore.put("practiceWpm", wpm.toString()) }) { Text("Slower") }
                        Text("$wpm WPM", style = JrType.label)
                        TextButton(enabled = wpm < 600, onClick = { wpm += 25; SettingsStore.put("practiceWpm", wpm.toString()) }) { Text("Faster") }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
        confirmButton = {
            if (words.isNotEmpty()) Button(onClick = {
                if (index == words.size) index = 0
                running = !running
            }) { Text(when { running -> "Pause"; index == words.size -> "Repeat"; index > 0 -> "Resume"; else -> "Start" }) }
        },
    )
}
