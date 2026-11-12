package com.emotionalleaves.studioagent.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.OutlinedTextFieldDefaults
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
            TextButton(onClick = { val r = Undo.restore; Undo.clear(); if (r != null) scope.io { r() } }) { Text("Undo", color = MaterialTheme.colorScheme.inversePrimary) }
        }
    }
}

@Composable
fun H(text: String) = Section(text)

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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Small(if (at >= opened) "Saved at " + Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(clockFmt) + ". Nothing else to do." else "Saves as you type. Nothing else to do.")
    }
}

fun fitLabel(score: Int): String = if (st.fitWords) Texts.fitWord(score) else "Fit $score of 100"

@Composable
fun Field(label: String, value: String, onValue: (String) -> Unit, lines: Int = 1, number: Boolean = false, secret: Boolean = false, mic: Boolean = false) {
    val speak = if (mic) rememberMic { said -> onValue((value + " " + said).trim()) } else null
    // Short labels float inside the box; long ones (a question, a hint) sit above it so they never wrap into the border.
    val above = label.length > 40
    if (above) Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    OutlinedTextField(
        value = value, onValueChange = onValue, label = if (above || label.isBlank()) null else ({ Text(label) }), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        singleLine = lines == 1, minLines = lines,
        trailingIcon = if (speak != null) ({ IconButton(onClick = speak) { MicIcon() } }) else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else if (secret) KeyboardType.Password else KeyboardType.Text,
            capitalization = if (number || secret) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            imeAction = if (lines == 1) androidx.compose.ui.text.input.ImeAction.Next else androidx.compose.ui.text.input.ImeAction.Default
        ),
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

/** A text field bound to a remembered string. */
@Composable
fun Field(label: String, state: MutableState<String>, lines: Int = 1, number: Boolean = false, mic: Boolean = false) =
    Field(label, state.value, { state.value = it }, lines, number, mic = mic)

@Composable
fun Choice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    // Two or three short options that switch a view read best as one joined bar, like tabs.
    if (label.isBlank() && options.size in 2..3 && options.all { it.length <= 12 } && selected in options) { Segments(options, selected, onSelect); return }
    Column {
        if (label.isNotBlank()) Small(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = if (o == selected) FontWeight.Bold else FontWeight.Normal) }) }
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Opens the phone's speech-to-text and hands back what was said. */
@Composable
fun rememberMic(onText: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    return {
        try {
            launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        } catch (e: Exception) { }
    }
}

/** A small drawn microphone, so the app needs no extra icon pack. */
@Composable
fun MicIcon() {
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(22.dp).semantics { contentDescription = "Speak instead of typing" }) {
        val w = size.width; val h = size.height; val sw = w * 0.09f
        drawRoundRect(c, topLeft = Offset(w * 0.36f, h * 0.08f), size = Size(w * 0.28f, h * 0.50f), cornerRadius = CornerRadius(w * 0.14f, w * 0.14f))
        drawArc(c, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.22f, h * 0.20f), size = Size(w * 0.56f, h * 0.52f), style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(c, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.90f), sw, StrokeCap.Round)
        drawLine(c, Offset(w * 0.34f, h * 0.90f), Offset(w * 0.66f, h * 0.90f), sw, StrokeCap.Round)
    }
}

/** A tick in a filled circle when done, a ring when not, and a ring with a dot for the one to do now. */
@Composable
fun Mark(done: Boolean, current: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val m = Modifier.size(20.dp)
    if (done) Box(m.background(cs.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = cs.onPrimary) }
    else Box(m.border(2.dp, if (current) cs.primary else cs.outline, CircleShape), contentAlignment = Alignment.Center) {
        if (current) Box(Modifier.size(8.dp).background(cs.primary, CircleShape))
    }
}

/** One line of a checklist. Screen readers hear "done" or "not done yet" instead of a symbol. */
@Composable
fun Check(done: Boolean, text: String, current: Boolean = false, bold: Boolean = false, small: Boolean = false) {
    Row(Modifier.semantics(mergeDescendants = true) { stateDescription = if (done) "done" else if (current) "do this now" else "not done yet" },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp)) { Mark(done, current) }
        Text(text, Modifier.weight(1f), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge)
    }
}

/** A small remove button with a full-size touch area and a spoken name. */
@Composable
fun RemoveButton(what: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Close, "Remove $what", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** A short buzz for the few moments that matter: starting, stopping, approving, sending. */
@Composable
fun rememberBuzz(): () -> Unit {
    val h = LocalHapticFeedback.current
    return { try { h.performHapticFeedback(HapticFeedbackType.LongPress) } catch (e: Exception) { } }
}

/** Recently shown pictures, so lists do not flicker or re-read files while scrolling. */
private val thumbs = android.util.LruCache<String, ImageBitmap>(40)

/** Shows an image file, decoded small and off the main thread. */
@Composable
fun PathImage(path: String, modifier: Modifier = Modifier, maxSide: Int = 700, scale: ContentScale = ContentScale.Fit) {
    val stamp = if (path.isBlank()) 0L else File(path).lastModified()
    val key = "$path|$stamp|$maxSide"
    val bmp by produceState<ImageBitmap?>(thumbs.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { ImageTools.decode(path, maxSide)?.asImageBitmap()?.also { thumbs.put(key, it) } }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = scale)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

/** A plain-words answer behind a tap, for any term the app uses that nobody should be expected to know already. */
@Composable
fun Explain(question: String, answer: String) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(question, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable(role = Role.Button) { open = !open }.semantics { stateDescription = if (open) "open" else "closed" }.heightIn(min = 40.dp).padding(vertical = 10.dp))
        AnimatedVisibility(open) { Panel { Text(answer) } }
    }
}

/** A section that stays folded until asked for, so long screens start short. */
@Composable
fun Expand(title: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "turn")
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }.semantics { stateDescription = if (open) "open" else "closed" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.rotate(turn), tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(open) { Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
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
    Text(text.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium,
        color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.background(if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
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
    Row(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        EmptyFrame()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action.isNotBlank()) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

/** A bare frame hanging from a nail: the app's picture for "nothing here yet". */
@Composable
fun EmptyFrame() {
    val line = MaterialTheme.colorScheme.outline
    val wall = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(width = 44.dp, height = 56.dp)) {
        val w = size.width; val h = size.height; val s = 2.dp.toPx()
        drawCircle(line, radius = 2.5f.dp.toPx(), center = Offset(w / 2, 3.dp.toPx()))
        drawLine(line, Offset(w / 2, 3.dp.toPx()), Offset(w * 0.18f, h * 0.30f), s * 0.6f)
        drawLine(line, Offset(w / 2, 3.dp.toPx()), Offset(w * 0.82f, h * 0.30f), s * 0.6f)
        drawRect(line, topLeft = Offset(w * 0.06f, h * 0.30f), size = Size(w * 0.88f, h * 0.68f), style = Stroke(s))
        drawRect(wall, topLeft = Offset(w * 0.22f, h * 0.42f), size = Size(w * 0.56f, h * 0.44f), style = Stroke(s * 0.6f))
    }
}

/**
 * One stop on a line of steps: a mark on the left, joined to the next by a thin line,
 * filled in up to where you are. Put a run of these in a column with no spacing.
 */
@Composable
fun Timeline(done: Boolean, current: Boolean, last: Boolean, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).let { if (onClick != null) it.clickable(role = Role.Button) { onClick() } else it }) {
        Box(Modifier.width(28.dp).fillMaxHeight()) {
            if (!last) Box(Modifier.align(Alignment.TopCenter).padding(top = 24.dp).width(2.dp).fillMaxHeight().background(if (done) cs.primary else cs.outlineVariant))
            Box(Modifier.align(Alignment.TopCenter).padding(top = 2.dp)) { Mark(done, current) }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, bottom = 20.dp).heightIn(min = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))

@Composable
fun Status(text: String) {
    // What just happened, in a small tinted strip that is easy to spot and easy to ignore.
    if (text.isNotBlank()) Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
}


/** A joined bar of two or three options; the chosen one is filled. */
@Composable
fun Segments(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(9.dp)).background(if (on) cs.surfaceContainerLowest else Color.Transparent)
                .selectable(selected = on, role = Role.Tab) { onSelect(o) }, contentAlignment = Alignment.Center) {
                Text(o.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) cs.onSurface else cs.onSurfaceVariant)
            }
        }
    }
}
