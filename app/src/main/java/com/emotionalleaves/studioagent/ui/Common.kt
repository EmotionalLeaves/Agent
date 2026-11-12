package com.emotionalleaves.studioagent.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.media.ImageTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val dao get() = G.dao
val st get() = G.settings

fun now() = System.currentTimeMillis()

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
fun fmtDate(ms: Long): String = if (ms <= 0) "no date" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFmt)
fun money(d: Double): String = (if (d < 0) "-" else "") + st.currency + "%.2f".format(kotlin.math.abs(d))
fun jargon(text: String): List<String> = Texts.jargonIn(text, st.jargonExtra)

/** Run database or network work off the main thread; errors land in [onError] instead of crashing. */
fun CoroutineScope.io(onError: (String) -> Unit = {}, block: suspend () -> Unit) = launch(Dispatchers.IO) {
    try { block() } catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "Something went wrong") } }
}

/** The last thing removed, kept for a short while so a slip of the thumb costs nothing. */
object Undo {
    val label = mutableStateOf("")
    var restore: (suspend () -> Unit)? = null
    fun clear() { label.value = ""; restore = null }
}

/** Removes something now and offers to put it back, instead of asking "are you sure?" beforehand. */
fun CoroutineScope.remove(label: String, delete: suspend () -> Unit, restore: suspend () -> Unit) {
    io { delete(); Undo.restore = restore; Undo.label.value = label }
}

@Composable
fun UndoBar() {
    val label = Undo.label.value
    if (label.isBlank()) return
    val scope = rememberCoroutineScope()
    LaunchedEffect(label) { delay(30_000); if (Undo.label.value == label) Undo.clear() }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Removed $label", Modifier.weight(1f), color = MaterialTheme.colorScheme.inverseOnSurface)
            TextButton(onClick = { val r = Undo.restore; Undo.clear(); if (r != null) scope.io { r() } }) { Text("Put it back", color = MaterialTheme.colorScheme.inversePrimary) }
        }
    }
}

@Composable
fun Screen(
    title: String, nav: NavController, back: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { UndoBar() },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { if (back) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content
        )
    }
}

@Composable
fun H(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp).semantics { heading() })

/** Small grey explanation. Hidden when "fewer words" is on, so screens can be as quiet as you like. */
@Composable
fun Note(text: String) { if (!st.shortText) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

/** Small grey text that always shows: labels and facts, not explanations. */
@Composable
fun Small(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** When the last change on an editing screen reached storage. Seeing it once replaces going back to check. */
val lastSaved = mutableStateOf(0L)
fun markSaved() { lastSaved.value = System.currentTimeMillis() }
private val clockFmt = DateTimeFormatter.ofPattern("h:mm:ss a")

@Composable
fun SavedLine() {
    val opened = remember { System.currentTimeMillis() }
    val at = lastSaved.value
    Small(if (at >= opened) "Saved ✓ at " + Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(clockFmt) + ". Nothing else to do." else "Everything here saves by itself as you type.")
}

fun fitLabel(score: Int): String = if (st.fitWords) Texts.fitWord(score) else "Fit $score of 100"

/**
 * The app's card. Plain panels are white with a hairline edge; [strong] ones are tinted for the one thing that matters on a screen.
 * A panel you can tap shows a small arrow, so it is obvious without trying.
 */
@Composable
fun Panel(onClick: (() -> Unit)? = null, strong: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
    val m = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }
    Card(modifier = m, colors = colors, border = if (strong) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
            if (onClick != null) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun Field(label: String, value: String, onValue: (String) -> Unit, lines: Int = 1, number: Boolean = false, secret: Boolean = false, mic: Boolean = false) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
            singleLine = lines == 1, minLines = lines,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (number) KeyboardType.Decimal else if (secret) KeyboardType.Password else KeyboardType.Text,
                capitalization = if (number || secret) KeyboardCapitalization.None else KeyboardCapitalization.Sentences
            ),
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
        )
        if (mic) MicButton { said -> onValue((value + " " + said).trim()) }
    }
}

/** A text field bound to a remembered string. */
@Composable
fun Field(label: String, state: MutableState<String>, lines: Int = 1, number: Boolean = false, mic: Boolean = false) =
    Field(label, state.value, { state.value = it }, lines, number, mic = mic)

@Composable
fun Choice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        if (label.isNotBlank()) Small(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o.replace('_', ' ')) }) }
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun MicButton(onText: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    TextButton(onClick = {
        try {
            launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        } catch (e: Exception) { }
    }) { Text("Speak instead") }
}

/** Recently shown pictures, so lists do not flicker or re-read files while scrolling. */
private val thumbs = android.util.LruCache<String, ImageBitmap>(40)

/** Shows an image file, decoded small and off the main thread. */
@Composable
fun PathImage(path: String, modifier: Modifier = Modifier, maxSide: Int = 700) {
    val stamp = if (path.isBlank()) 0L else File(path).lastModified()
    val key = "$path|$stamp|$maxSide"
    val bmp by produceState<ImageBitmap?>(thumbs.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { ImageTools.decode(path, maxSide)?.asImageBitmap()?.also { thumbs.put(key, it) } }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = ContentScale.Fit)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

/** A plain-words answer behind a tap, for any term the app uses that nobody should be expected to know already. */
@Composable
fun Explain(question: String, answer: String) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(question, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { open = !open }.padding(vertical = 6.dp))
        if (open) Panel { Text(answer) }
    }
}

/** A section that stays folded until asked for, so long screens start short. */
@Composable
fun Expand(title: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (open) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
        }
        if (open) content()
    }
}

/** One short explanation the first time a screen is opened. Gone for good after "Got it". */
@Composable
fun Intro(key: String, text: String) {
    val tick by st.changed.collectAsState()
    if (tick >= 0 && key !in st.seenIntros.split(",")) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { st.seenIntros = st.seenIntros + "," + key }) { Text("Got it") }
        }
    }
}

/** A size box that shows inches or centimetres, whichever you chose, and always stores inches. */
@Composable
fun Dim(label: String, valueIn: Double, onIn: (Double) -> Unit) {
    val cm = st.units == "cm"
    Num("$label, " + (if (cm) "cm" else "in"), if (cm) Math.round(valueIn * 25.4) / 10.0 else valueIn) { onIn(if (cm) it / 2.54 else it) }
}

/** A small label for one fact about a thing: a status, a date, a price. */
@Composable
fun Tag(text: String, strong: Boolean = false) {
    Text(text.replace('_', ' '), style = MaterialTheme.typography.bodySmall,
        color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.background(if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** A piece's picture in a list, or its initial on a quiet tile when there is no picture yet. */
@Composable
fun Thumb(path: String, title: String, size: Int = 64) {
    val shape = RoundedCornerShape(10.dp)
    if (path.isNotBlank() && File(path).exists()) PathImage(path, Modifier.size(size.dp).clip(shape), 220)
    else Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape), contentAlignment = Alignment.Center) {
        Text(title.trim().take(1).uppercase().ifBlank { "·" }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.outline)
    }
}

/** What a list shows when it is empty: one line of what goes here and, at most, one thing to tap. */
@Composable
fun Empty(text: String, action: String = "", onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action.isNotBlank()) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))

@Composable
fun Status(text: String) { if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
