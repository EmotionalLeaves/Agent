package com.emotionalleaves.studioagent.ui

import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.R

/*
 * The look of the app, in one place.
 *
 * Gallery-wall white, graphite text, pencil-grey notes, one cobalt for actions,
 * and masking-tape yellow only for "this is the thing to do now".
 * Titles in Bricolage Grotesque; everything you read in Atkinson Hyperlegible, a face made to be easy to read.
 */

val Wall = Color(0xFFF2F2EF)
val Graphite = Color(0xFF2A2C2E)
val Pencil = Color(0xFF63676B)
val Cobalt = Color(0xFF2B4ACB)
val Tape = Color(0xFFF1E3A6)
val Hairline = Color(0xFFDADBD6)

val DisplayFace = FontFamily(
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold)
)
val BodyFace = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
    Font(R.font.atkinson_italic, FontWeight.Normal, FontStyle.Italic)
)

private val LightColors = lightColorScheme(
    primary = Cobalt, onPrimary = Color.White, primaryContainer = Tape, onPrimaryContainer = Graphite,
    secondary = Color(0xFFA4441F), onSecondary = Color.White, secondaryContainer = Color(0xFFDDE3FA), onSecondaryContainer = Color(0xFF14256C),
    tertiary = Color(0xFF6D5A12), onTertiary = Color.White, tertiaryContainer = Tape, onTertiaryContainer = Graphite,
    background = Wall, onBackground = Graphite, surface = Wall, onSurface = Graphite,
    surfaceVariant = Color(0xFFE6E7E3), onSurfaceVariant = Pencil, outline = Color(0xFF7A7E83), outlineVariant = Hairline,
    surfaceTint = Color.Transparent, inverseSurface = Graphite, inverseOnSurface = Wall, inversePrimary = Color(0xFFA9B9FF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7F5), surfaceContainer = Color(0xFFEBEBE7),
    surfaceContainerHigh = Color(0xFFE4E5E1), surfaceContainerHighest = Color(0xFFDDDED9), surfaceBright = Color.White, surfaceDim = Color(0xFFDADBD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3B4FF), onPrimary = Color(0xFF0E1A55), primaryContainer = Color(0xFF4A4224), onPrimaryContainer = Color(0xFFF6ECC4),
    secondary = Color(0xFFF0A07E), onSecondary = Color(0xFF4A1A06), secondaryContainer = Color(0xFF283157), onSecondaryContainer = Color(0xFFDDE3FA),
    tertiary = Color(0xFFE2CF86), onTertiary = Color(0xFF3A2F00), tertiaryContainer = Color(0xFF4A4224), onTertiaryContainer = Color(0xFFF6ECC4),
    background = Color(0xFF1B1C1E), onBackground = Color(0xFFE9E9E5), surface = Color(0xFF1B1C1E), onSurface = Color(0xFFE9E9E5),
    surfaceVariant = Color(0xFF34363A), onSurfaceVariant = Color(0xFFB7BABE), outline = Color(0xFF85898E), outlineVariant = Color(0xFF3A3C40),
    surfaceTint = Color.Transparent, inverseSurface = Color(0xFFE9E9E5), inverseOnSurface = Color(0xFF1B1C1E), inversePrimary = Cobalt,
    surfaceContainerLowest = Color(0xFF232427), surfaceContainerLow = Color(0xFF202124), surfaceContainer = Color(0xFF26272A),
    surfaceContainerHigh = Color(0xFF2E2F33), surfaceContainerHighest = Color(0xFF37393D), surfaceBright = Color(0xFF3A3B3F), surfaceDim = Color(0xFF1B1C1E)
)

private fun d(size: Int, line: Int, w: FontWeight = FontWeight.SemiBold, track: Double = -0.2) =
    TextStyle(fontFamily = DisplayFace, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp)
private fun b(size: Double, line: Int, w: FontWeight = FontWeight.Normal, track: Double = 0.1) =
    TextStyle(fontFamily = BodyFace, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp)

private val AppType = Typography(
    displayLarge = d(64, 66, FontWeight.Medium, -1.5), displayMedium = d(56, 60, FontWeight.Medium, -1.0), displaySmall = d(36, 40, FontWeight.SemiBold, -0.8),
    headlineLarge = d(32, 36, track = -0.6), headlineMedium = d(28, 32, track = -0.5), headlineSmall = d(23, 28, track = -0.3),
    titleLarge = d(20, 26), titleMedium = d(17, 22, track = -0.1), titleSmall = b(15.0, 20, FontWeight.Bold),
    bodyLarge = b(16.0, 24), bodyMedium = b(15.0, 22), bodySmall = b(13.5, 19),
    labelLarge = b(15.0, 20, FontWeight.Bold), labelMedium = b(13.0, 18), labelSmall = b(12.0, 16)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, typography = AppType, shapes = AppShapes, content = content)
}

/* Buttons: squared-off rather than pill-shaped, and tall enough to hit without looking. */
private val ButtonShape = RoundedCornerShape(12.dp)
private val ButtonPad = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.Button(onClick, modifier.heightIn(min = 50.dp), enabled, shape = ButtonShape, contentPadding = ButtonPad, content = content)

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.OutlinedButton(onClick, modifier.heightIn(min = 50.dp), enabled, shape = ButtonShape, contentPadding = ButtonPad, content = content)

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.TextButton(onClick, modifier.heightIn(min = 48.dp), enabled, shape = ButtonShape, content = content)

/** The help text of the screen being drawn. [Intro] fills it; the header shows a "?" that opens it. */
val LocalHelp = staticCompositionLocalOf<MutableState<String>?> { null }

/** What a screen is for. Not printed on the page: it waits behind the header's info button so screens stay short. */
@Composable
fun Intro(@Suppress("UNUSED_PARAMETER") key: String, text: String) {
    val h = LocalHelp.current ?: return
    SideEffect { if (h.value != text) h.value = text }
}

/**
 * Every screen. Main tabs ([back] false) get a large title and no back arrow; deeper screens get a slim bar with one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String, nav: NavController, back: Boolean = true,
    bare: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val help = rememberSaveable { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val helpButton: @Composable () -> Unit = {
        if (help.value.isNotBlank()) IconButton(onClick = { showHelp = true }) { Icon(Icons.Outlined.Info, "What is this screen for?", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    CompositionLocalProvider(LocalHelp provides help) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { UndoBar() },
            topBar = {
                if (back && !bare) TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = { actions(); helpButton() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { pad ->
            Column(
                Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = if (bare) 24.dp else if (back) 4.dp else 0.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!back && !bare) Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.displaySmall)
                    actions(); helpButton()
                }
                content()
            }
        }
        if (showHelp) ModalBottomSheet(onDismissRequest = { showHelp = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(help.value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * Three kinds of block, by what they are for:
 * a tappable one is a plain row with a hairline under it, like a list;
 * a [strong] one is a taped-up label for the one thing to do now;
 * anything else is a white sheet.
 */
@Composable
fun Panel(onClick: (() -> Unit)? = null, strong: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (onClick != null && !strong) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onClick() }.heightIn(min = 56.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.padding(start = 8.dp), tint = cs.outline)
            }
            HorizontalDivider(color = cs.outlineVariant)
        }
        return
    }
    val shape = if (strong) RoundedCornerShape(3.dp) else RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (strong) cs.primaryContainer else cs.surfaceContainerLowest)
            .let { if (onClick != null) it.clickable(role = Role.Button) { onClick() } else it }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val scope = this
        CompositionLocalProvider(LocalContentColor provides if (strong) cs.onPrimaryContainer else cs.onSurface) { scope.content() }
    }
}

/** A strip of masking tape, drawn at a slight angle over the top edge of whatever it sits on. */
@Composable
fun TapeStrip(modifier: Modifier = Modifier, angle: Float = -3f) {
    val tape = if (isSystemInDarkTheme()) Color(0xFFCDBB72) else Color(0xFFE9D27E)
    Box(modifier.rotate(angle).size(width = 92.dp, height = 22.dp).background(tape.copy(alpha = 0.92f)))
}

/** A label pinned to the wall: white sheet, tape across the top. Used for the one next step. */
@Composable
fun WallLabel(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(cs.surfaceContainerLowest)
                .let { if (onClick != null) it.clickable(role = Role.Button) { onClick() } else it }
                .padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content
        )
        TapeStrip(Modifier.align(Alignment.TopCenter).offset(y = (-10).dp))
    }
}

/** A plain section heading with a little room above it. */
@Composable
fun Section(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp).semantics { heading() })

@Composable
fun Hair() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun Space(h: Int) = Spacer(Modifier.height(h.dp))

/** A list row with an icon on the left: for menus of places to go, like Settings. */
@Composable
fun IconRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String = "", onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onClick() }.heightIn(min = 60.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.outline)
        }
        HorizontalDivider(color = cs.outlineVariant)
    }
}

/** For deleting: quiet, red, and always undoable from the bar that appears. */
@Composable
fun DangerButton(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.TextButton(onClick, Modifier.heightIn(min = 48.dp), shape = ButtonShape,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), content = content)

/** A small count with − and + beside it: nothing to type, nothing to get wrong. */
@Composable
fun Count(label: String, value: Int, min: Int = 0, max: Int = 99, step: Int = 1, unit: String = "", onValue: (Int) -> Unit) {
    // Keyed on the value, so a change from elsewhere (a page read for you) shows at once.
    var v by remember(value) { mutableStateOf(value.coerceIn(min, max)) }
    val cs = MaterialTheme.colorScheme
    fun set(n: Int) { v = n.coerceIn(min, max); onValue(v) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        StepButton("−", "Less $label", v > min) { set(v - step) }
        Column(Modifier.size(width = 64.dp, height = 48.dp).semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("$v", style = MaterialTheme.typography.titleLarge)
            if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        StepButton("+", "More $label", v < max) { set(v + step) }
    }
}

@Composable
private fun StepButton(sign: String, spoken: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tap by androidx.compose.runtime.rememberUpdatedState(onClick)
    val on by androidx.compose.runtime.rememberUpdatedState(enabled)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Hold to keep counting, so 120 minutes is one press, not twenty-three taps.
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(if (enabled) cs.surfaceContainerLowest else cs.surfaceContainer)
        .pointerInput(Unit) {
            androidx.compose.foundation.gestures.awaitEachGesture {
                androidx.compose.foundation.gestures.awaitFirstDown()
                if (on) {
                    tap()
                    val repeat = scope.launch { kotlinx.coroutines.delay(450); while (on) { tap(); kotlinx.coroutines.delay(90) } }
                    androidx.compose.foundation.gestures.waitForUpOrCancellation()
                    repeat.cancel()
                }
            }
        }
        .semantics { role = Role.Button; contentDescription = spoken; onClick(label = spoken) { if (on) tap(); on }; if (!enabled) disabled() }, contentAlignment = Alignment.Center) {
        Text(sign, style = MaterialTheme.typography.titleLarge, color = if (enabled) cs.primary else cs.outline)
    }
}

/** A time of day, chosen on the phone's own clock face. Stored as HH:MM. */
@Composable
fun TimeField(label: String, value: String, onValue: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var v by remember(value) { mutableStateOf(value) }
    val (h, m) = v.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 7) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
            android.app.TimePickerDialog(ctx, { _, hh, mm -> v = "%02d:%02d".format(hh, mm); onValue(v) }, h, m, android.text.format.DateFormat.is24HourFormat(ctx)).show()
        }, verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            val shown = java.time.LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
            Text(shown, Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Hair()
    }
}

/** Seven days as seven circles to tap. Stored as "1,2,3" with 1 = Monday. */
@Composable
fun DaysField(label: String, value: String, onValue: (String) -> Unit) {
    var on by remember { mutableStateOf(value.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()) }
    val cs = MaterialTheme.colorScheme
    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..7).forEach { d ->
                val picked = d in on
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(if (picked) cs.primary else cs.surfaceContainerLowest)
                    .toggleable(value = picked, role = Role.Checkbox) { c -> on = if (c) on + d else on - d; onValue(on.sorted().joinToString(",")) }
                    .semantics { contentDescription = names[d - 1] }, contentAlignment = Alignment.Center) {
                    Text("MTWTFSS"[d - 1].toString(), style = MaterialTheme.typography.labelLarge, color = if (picked) cs.onPrimary else cs.onSurface)
                }
            }
        }
    }
}

/** While something is being read from storage: a quiet bar, no words to read. */
@Composable
fun Loading() {
    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 24.dp).height(3.dp),
        color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
}

/** A date, chosen on the phone's own calendar. Stored as YYYY-MM-DD. */
@Composable
fun DateField(label: String, value: String, onValue: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val d = try { java.time.LocalDate.parse(value) } catch (e: Exception) { java.time.LocalDate.now() }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
            android.app.DatePickerDialog(ctx, { _, y, m, day -> onValue(java.time.LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }, verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(if (value.isBlank()) "Choose a date" else fmtDate(d.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()),
                Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Hair()
    }
}
