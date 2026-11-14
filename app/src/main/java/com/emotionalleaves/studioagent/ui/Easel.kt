package com.emotionalleaves.studioagent.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emotionalleaves.studioagent.data.Artwork
import kotlin.random.Random

/**
 * The easel: Today's one step, painted over your own work. The piece you are on fills the card; with no picture yet,
 * a color field painted from the app's colors stands in. The step is the biggest thing on the screen, and Start is
 * one thumb's reach below it.
 */
@Composable
fun Easel(
    title: String, detail: String, piece: Artwork?, dateLine: String, minutes: Int, inSession: Boolean, startLabel: String? = null,
    onOpen: () -> Unit, onStart: () -> Unit, onTired: () -> Unit, onSession: () -> Unit, menu: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().height(460.dp).clip(RoundedCornerShape(28.dp)).background(cs.primary)
            .clickable(role = Role.Button, onClickLabel = "Open today's step") { onOpen() }
    ) {
        if (piece != null && piece.imagePath.isNotBlank())
            PathImage(piece.imagePath, Modifier.matchParentSize(), 1200, ContentScale.Crop, description = "Your piece: " + piece.title.ifBlank { "untitled" })
        else ColorField(cs.primary, Modifier.matchParentSize())
        // Dark at the top for the date, clear through the middle so the work shows, dark at the foot for the words.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.38f), 0.22f to Color.Transparent, 0.48f to Color.Black.copy(alpha = 0.10f), 1f to Color.Black.copy(alpha = 0.86f))))
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dateLine, Modifier.weight(1f), color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.labelLarge)
            menu()
        }
        // The one orchestrated moment in the app: the first time Today opens after the app starts, the step rises into place.
        // After that it is simply there, so it never gets in the way of a quick look.
        var risen by remember { mutableStateOf(EaselIntro.done) }
        LaunchedEffect(Unit) { EaselIntro.done = true; risen = true }
        val rise by animateFloatAsState(if (risen) 1f else 0f, tween(620, easing = FastOutSlowInEasing), label = "easel")
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp).graphicsLayer { alpha = rise; translationY = (1f - rise) * 36.dp.toPx() }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (piece != null && piece.title.isNotBlank()) Text(piece.title, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
            Text(title, color = Color.White, style = MaterialTheme.typography.displaySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            if (inSession) Pill("Back to your session", Modifier.fillMaxWidth(), onSession)
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(startLabel ?: "Start, $minutes minutes", Modifier.weight(1f), onStart)
                // The tired-day door, the same size of target but quieter.
                if (startLabel == null) Box(Modifier.size(56.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Just 5 minutes") { onTired() }.semantics { contentDescription = "Tired? Just 5 minutes" },
                    contentAlignment = Alignment.Center) { Text("5", color = Color.White, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** Whether Today's step has risen into place once since the app started. */
object EaselIntro { var done = false }

/** A white pill with the play mark: the one big action. */
@Composable
private fun Pill(label: String, modifier: Modifier, onClick: () -> Unit) {
    val f = FloatArray(3); val p = MaterialTheme.colorScheme.primary
    androidx.core.graphics.ColorUtils.colorToHSL(android.graphics.Color.rgb((p.red * 255).toInt(), (p.green * 255).toInt(), (p.blue * 255).toInt()), f)
    val ink = hsl(f[0], f[1].coerceAtLeast(0.4f), 0.26f)
    Row(modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).clickable(role = Role.Button) { onClick() }.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Default.PlayArrow, null, tint = ink)
        Spacer(Modifier.size(6.dp))
        Text(label, color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * A color field painted from one color: soft stacked blocks in its neighbors, the way a painter blocks in a canvas,
 * with a little grain. Different every day, the same all of one day.
 */
@Composable
fun ColorField(base: Color, modifier: Modifier = Modifier, key: Long = -1L, shift: Float = 0f) {
    val day = remember(key) { if (key >= 0) key else java.time.LocalDate.now().toEpochDay() }
    val f = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(android.graphics.Color.argb((base.alpha * 255).toInt(), (base.red * 255).toInt(), (base.green * 255).toInt(), (base.blue * 255).toInt()), f)
    val h = f[0] + shift
    Canvas(modifier) {
        val r = Random(day)
        drawRect(hsl(h, 0.45f, 0.18f))
        // Two or three soft blocks in the color's near neighbors, stacked like a painting blocked in, never the same twice.
        val all = listOf(hsl(h - 22f, 0.48f, 0.40f), hsl(h + 14f, 0.42f, 0.56f), hsl(h + 4f, 0.55f, 0.30f), hsl(h - 8f, 0.30f, 0.66f))
        val n = 2 + r.nextInt(2)
        val blocks = all.shuffled(r).take(n)
        var y = size.height * (0.05f + r.nextFloat() * 0.05f)
        val gap = size.height * 0.035f
        val each = (size.height * 0.9f - gap * (n - 1)) / n
        blocks.forEach { c ->
            val bh = each * (0.8f + r.nextFloat() * 0.35f)
            val inset = size.width * (0.06f + r.nextFloat() * 0.05f)
            // Feathered edges: the same block a few times, each a little larger and fainter.
            for (k in 6 downTo 0) {
                val grow = k * 5f
                drawRoundRect(c.copy(alpha = if (k == 0) 0.9f else 0.05f), Offset(inset - grow, y - grow), Size(size.width - 2 * inset + 2 * grow, bh + 2 * grow), CornerRadius(14f + grow))
            }
            y += bh + gap
        }
        repeat(900) { drawCircle(Color.White.copy(alpha = 0.035f), 1.2f, Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)) }
    }
}

/**
 * The opening of a screen in the new face: a painted field (or a picture) with the screen's big words in white over it,
 * and room underneath for one line, a number or a bar. Every main screen starts this way, so they belong together.
 */
@Composable
fun Banner(
    title: String, sub: String = "", key: Long = 0L, image: String? = null, height: Int = 190,
    big: String? = null, bigLabel: String = "", onClick: (() -> Unit)? = null, extra: @Composable () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    // At least [height] tall, and taller when the words need it: a banner never cuts its own sentence off.
    Box(Modifier.fillMaxWidth().heightIn(min = height.dp).clip(RoundedCornerShape(26.dp)).let { if (onClick != null) it.clickable(role = Role.Button) { onClick() } else it }) {
        if (image != null && image.isNotBlank() && java.io.File(image).exists()) PathImage(image, Modifier.matchParentSize(), 1000, ContentScale.Crop)
        else ColorField(cs.primary, Modifier.matchParentSize(), key = key, shift = ((key * 37) % 70 - 35).toFloat())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.08f), 1f to Color.Black.copy(alpha = 0.78f))))
        if (big != null) Column(Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.32f)).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            if (bigLabel.isNotBlank()) Text(bigLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 44.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // With a number badge in the corner, the title keeps clear of it.
            Text(title, Modifier.padding(end = if (big != null) 72.dp else 0.dp), style = if (title.length > 26) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge, color = Color.White)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.86f))
            extra()
        }
    }
}

/** The button that sits on a painted banner: solid white for the main choice, a white outline for the other. */
@Composable
fun WhitePill(label: String, filled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.clip(shape).let { if (filled) it.background(Color.White) else it.border(1.5.dp, Color.White, shape) }
        .clickable(role = Role.Button) { onClick() }.padding(horizontal = 18.dp, vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (filled) Color.Black else Color.White)
    }
}
