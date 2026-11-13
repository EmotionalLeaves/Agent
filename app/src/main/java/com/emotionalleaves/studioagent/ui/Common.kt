package com.emotionalleaves.studioagent.ui

import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.MenuAnchorType

import androidx.compose.material3.ExposedDropdownMenuDefaults

import androidx.compose.material3.ExposedDropdownMenuBox

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

/** The phone's own date order with the weekday first: "Tue, Dec 22, 2026" in the US, "Tue, 22 Dec 2026" in the UK. */
private val dateFmt: DateTimeFormatter = try {
    DateTimeFormatter.ofPattern("EEE, " + java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        java.time.format.FormatStyle.MEDIUM, null, java.time.chrono.IsoChronology.INSTANCE, java.util.Locale.getDefault()))
} catch (e: Exception) { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }
/** A time of day in the phone's own style (5:00 PM or 17:00). */
fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

fun fmtDate(ms: Long): String = if (ms <= 0) "no date" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFmt)
/** Whole amounts without cents ($55), others with them ($23.25). */
fun money(d: Double): String = (if (d < 0) "-" else "") + st.currency + kotlin.math.abs(d).let { a -> if (Math.abs(a - Math.rint(a)) < 0.005) "%,.0f".format(a) else "%,.2f".format(a) }
fun jargon(text: String): List<String> = Texts.jargonIn(text, st.jargonExtra)

/** Run database or network work off the main thread; errors land in [onError] instead of crashing. */
fun CoroutineScope.io(onError: (String) -> Unit = {}, block: suspend () -> Unit) = launch(Dispatchers.IO) {
    try { block() } catch (e: Exception) { withContext(Dispatchers.Main) { onError(plainError(e)) } }
}

/** Turns a technical failure into what happened and what to do, in plain words. Messages the app wrote itself pass through. */
fun plainError(e: Throwable): String {
    val m = e.message.orEmpty()
    return when {
        e is java.net.UnknownHostException || e is java.net.ConnectException || m.contains("Unable to resolve host") -> "No internet connection right now. Nothing was lost; try again when you are online."
        e is java.net.SocketTimeoutException || m.contains("timeout", true) -> "The other end took too long to answer. Try again in a minute."
        m.contains("401") || m.contains("403") || m.contains("invalid x-api-key", true) || m.contains("authentication", true) -> "The key or password was not accepted. Check it in You, Settings, under Writing help or Email."
        m.contains("429") || m.contains("rate limit", true) -> "The service is busy. Try again in a few minutes."
        e is javax.net.ssl.SSLException -> "A secure connection could not be made. Try again on another network."
        m.isBlank() || m.startsWith("java.") || m.startsWith("kotlin.") || m.length > 220 -> "Something went wrong. Nothing was lost; try again."
        else -> m
    }
}

/** The last thing removed, kept for a short while so a slip of the thumb costs nothing. */
object Undo {
    val label = mutableStateOf("")
    var restore: (suspend () -> Unit)? = null
    fun clear() { label.value = ""; restore = null }
}

/** Removes something now and offers to put it back, instead of asking "are you sure?" beforehand. */
fun CoroutineScope.remove(label: String, delete: suspend () -> Unit, restore: suspend () -> Unit) = undoable("Removed $label", delete, restore)

/** Does something now and shows one line with Undo for 30 seconds. [message] is the whole line, for example "Said no to Winter Open". */
fun CoroutineScope.undoable(message: String, act: suspend () -> Unit, restore: suspend () -> Unit) {
    io { act(); Undo.restore = restore; Undo.label.value = message }
}

@Composable
fun UndoBar() {
    val label = Undo.label.value
    if (label.isBlank()) return
    val scope = rememberCoroutineScope()
    LaunchedEffect(label) { delay(30_000); if (Undo.label.value == label) Undo.clear() }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.inverseOnSurface)
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
    // The keyboard fits the box: @ for email, / and .com for web addresses, digits for phone numbers.
    val l = label.lowercase()
    val kind = when {
        number -> KeyboardType.Decimal; secret -> KeyboardType.Password
        lines > 1 || l.startsWith("paste") || l.startsWith("first") -> KeyboardType.Text
        l.startsWith("phone") -> KeyboardType.Phone
        Regex("\\bemail\\b|sender address").containsMatchIn(l) && !l.contains("email to") -> KeyboardType.Email
        Regex("website|web address|page address|\\blink\\b|\\burl\\b|their site").containsMatchIn(l) -> KeyboardType.Uri
        else -> KeyboardType.Text
    }
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
            keyboardType = kind,
            capitalization = if (number || secret || kind != KeyboardType.Text) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
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
fun Choice(label: String, options: List<String>, selected: String, names: ((String) -> String)? = null, onSelect: (String) -> Unit) {
    // Two or three short options that switch a view read best as one joined bar, like tabs.
    if (names == null && label.isBlank() && options.size in 2..3 && options.all { it.length <= 12 } && selected in options) { Segments(options, selected, onSelect); return }
    // Long lists become one box that opens a list, instead of a wall of chips.
    if (options.size > 5) { Pick(label, options, selected, names, onSelect); return }
    Column {
        if (label.isNotBlank()) Small(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(names?.invoke(o) ?: o.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = if (o == selected) FontWeight.Bold else FontWeight.Normal) }) }
        }
    }
}

/** One tidy box showing the current choice; tap to see the list. */
@Composable
fun Pick(label: String, options: List<String>, selected: String, names: ((String) -> String)? = null, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val show = { o: String -> names?.invoke(o) ?: o.replace('_', ' ').replaceFirstChar { it.uppercase() } }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = if (selected in options) show(selected) else "", onValueChange = {}, readOnly = true, singleLine = true,
            label = if (label.isNotBlank()) ({ Text(label) }) else null, placeholder = { Text("Choose") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(show(o), fontWeight = if (o == selected) FontWeight.Bold else FontWeight.Normal) }, onClick = { open = false; onSelect(o) }) }
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
fun PathImage(path: String, modifier: Modifier = Modifier, maxSide: Int = 700, scale: ContentScale = ContentScale.Fit, description: String? = null) {
    val stamp = if (path.isBlank()) 0L else File(path).lastModified()
    val key = "$path|$stamp|$maxSide"
    val bmp by produceState<ImageBitmap?>(thumbs.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { ImageTools.decode(path, maxSide)?.asImageBitmap()?.also { thumbs.put(key, it) } }
    }
    val b = bmp
    if (b != null) Image(b, description, modifier, contentScale = scale)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

/** A plain-words answer behind a tap, for any term the app uses that nobody should be expected to know already. */
@Composable
fun Explain(question: String, answer: String) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(question, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable(role = Role.Button) { open = !open }.semantics { stateDescription = if (open) "open" else "closed" }.heightIn(min = 48.dp).padding(vertical = 12.dp))
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
        // Strong tags use the cobalt tint; tape yellow is kept for "do this now" only.
        color = if (strong) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.background(if (strong) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
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

/** One box that finds anything in the app: a piece, a call, a venue or person, a document. Each result opens where it lives. */
@Composable
fun SearchScreen(nav: NavController) {
    val q = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val works by dao.artworks().collectAsState(emptyList())
    val opps by dao.opps().collectAsState(emptyList())
    val contacts by dao.contacts().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val ideas by dao.ideas().collectAsState(emptyList())
    val sessionsAll by dao.sessions().collectAsState(emptyList())
    val eventsAll by dao.events().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    Screen("Find anything", nav) {
        Field("A title, a venue, a person, a word, or a question", q, mic = true)
        // A question gets an answer about your own situation, not a list of matches.
        val question = q.value.trim().let { t -> t.length > 8 && (t.endsWith("?") || Regex("^(how|what|when|where|which|who|why|should|can|could|do|does|is|am|are|will|would)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) }
        if (question) androidx.compose.material3.Button(enabled = !asking, onClick = {
            asking = true; answer = "Thinking it over…"
            scope.io({ answer = it; asking = false }) {
                val question = q.value.trim()
                // With the helper: a full answer. Without it, or when it does not answer: the common questions are worked out on the phone.
                val a = if (G.ai.ready) try { G.ai.askAnything(com.emotionalleaves.studioagent.work.Engine.snapshot(), question) } catch (e: Exception) { null } else null
                answer = a ?: com.emotionalleaves.studioagent.work.Engine.answerLocally(question) ?: "That one needs the writing helper, which is not answering just now. Try again in a minute, or turn it on in Settings."
                asking = false
            }
        }) { Text("Ask") }
        if (answer.isNotBlank()) {
            val go = Regex("(?m)^\\s*GO:\\s*(\\w+)\\s*$").find(answer)?.groupValues?.get(1)?.lowercase()
            val places = mapOf("calls" to "Open Calls", "week" to "This week", "works" to "Your pieces", "materials" to "Your materials", "outreach" to "People and venues", "nearby" to "Places near you", "records" to "Records", "guide" to "Help", "session" to "Start a session", "glossary" to "Words calls use") +
                toolGroups.flatMap { it.second }.associate { it.third to it.first }
            Panel {
                Text(answer.replace(Regex("(?m)^\\s*GO:.*$"), "").trim())
                if (go != null && go in places) androidx.compose.material3.TextButton(onClick = { nav.navigate(go) }) { Text("Go to " + places[go]) }
            }
            if (!asking && answer != "Thinking it over…") Small("A suggestion from the writing helper, from what is in the app. You know your situation best.")
        }
        val words = q.value.trim().lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        fun hit(vararg parts: String) = words.isNotEmpty() && words.all { w -> parts.any { it.lowercase().contains(w) } }
        val w = works.filter { hit(it.title, it.medium, it.tags, it.notes) }.take(8)
        val c = opps.filter { hit(it.title, it.organiser, it.venue, it.city, it.theme) }.take(8)
        val p = contacts.filter { hit(it.venue, it.name, it.email, it.notes) }.take(8)
        val d = docs.filter { it.type != "interview" && hit(it.title, it.text) }.take(5)
        // Your own notes too: ideas, and what you wrote at the end of sessions.
        val i = ideas.filter { hit(it.text) }.take(5)
        val sn = sessionsAll.filter { (it.note.isNotBlank() || it.nextStep.isNotBlank()) && hit(it.note, it.nextStep) }.take(5)
        // Your to-dos, and the app's own tools ("postcards", "teach", "prices"), so a feature is found by its name.
        val todo = eventsAll.filter { it.type == "task" && !it.done && hit(it.title) }.take(5)
        val tl = toolGroups.flatMap { it.second }.filter { hit(it.first, it.second) }.take(4)
        if (tl.isNotEmpty()) { Section("Tools"); tl.forEach { (t, why, r) -> IconRow(Icons.Default.Menu, t, why) { nav.navigate(r) } } }
        val setHits = settingIndex.filter { hit(it.first) }.take(4)
        if (setHits.isNotEmpty()) { Section("Settings"); setHits.forEach { (label, id) -> IconRow(Icons.Default.Settings, label, "Settings, " + (sectionNames.firstOrNull { it.first == id }?.second ?: "")) { nav.navigate("settings/$id") } } }
        if (!question && words.isNotEmpty() && w.isEmpty() && c.isEmpty() && p.isEmpty() && d.isEmpty() && i.isEmpty() && sn.isEmpty() && todo.isEmpty() && tl.isEmpty() && setHits.isEmpty()) Small("Nothing matches that.")
        if (todo.isNotEmpty()) { Section("To do"); todo.forEach { x -> IconRow(Icons.Default.DateRange, x.title, fmtDate(x.date)) { nav.navigate("records") } } }
        if (w.isNotEmpty()) { Section("Pieces"); w.forEach { x -> IconRow(Icons.Default.Edit, x.title.ifBlank { "Untitled" }, statusWords(x.status)) { nav.navigate("work/${x.id}") } } }
        if (c.isNotEmpty()) { Section("Calls"); c.forEach { x -> IconRow(Icons.Default.DateRange, x.title, listOf(x.organiser, x.city).filter { it.isNotBlank() }.joinToString(", ")) { nav.navigate("call/${x.id}") } } }
        if (p.isNotEmpty()) { Section("People and venues"); p.forEach { x -> IconRow(Icons.Default.Person, x.venue.ifBlank { x.name.ifBlank { x.email } }, if (x.kind == "subscriber") "On your email list" else kindWords(x.kind)) { nav.navigate("contact/${x.id}") } } }
        if (d.isNotEmpty()) { Section("Writing"); d.forEach { x -> IconRow(Icons.Default.Edit, x.title.ifBlank { x.type.replaceFirstChar { it.uppercase() } }, x.text.take(60)) { nav.navigate("doc/${x.id}") } } }
        if (i.isNotEmpty()) { Section("Ideas"); i.forEach { x -> IconRow(Icons.Default.Edit, x.text.take(60), fmtDate(x.createdAt)) { nav.navigate("ideas") } } }
        if (sn.isNotEmpty()) { Section("Session notes"); sn.forEach { x -> IconRow(Icons.Default.DateRange, listOf(x.note, x.nextStep).filter { it.isNotBlank() }.joinToString(" / ").take(70), fmtDate(x.start)) { nav.navigate(if (x.artworkId > 0) "work/${x.artworkId}" else "sessions") } } }
    }
}
