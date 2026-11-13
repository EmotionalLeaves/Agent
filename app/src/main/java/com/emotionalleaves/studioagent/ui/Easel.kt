package com.emotionalleaves.studioagent.ui

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
    title: String, detail: String, piece: Artwork?, dateLine: String, minutes: Int, inSession: Boolean,
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
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (piece != null && piece.title.isNotBlank()) Text(piece.title, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
            Text(title, color = Color.White, style = MaterialTheme.typography.displaySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            if (inSession) Pill("Back to your session", Modifier.fillMaxWidth(), onSession)
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Start, $minutes minutes", Modifier.weight(1f), onStart)
                // The tired-day door, the same size of target but quieter.
                Box(Modifier.size(56.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Just 5 minutes") { onTired() }.semantics { contentDescription = "Tired? Just 5 minutes" },
                    contentAlignment = Alignment.Center) { Text("5", color = Color.White, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** A white pill with the play mark: the one big action. */
@Composable
private fun Pill(label: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).clickable(role = Role.Button) { onClick() }.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Default.PlayArrow, null, tint = cs.primary)
        Spacer(Modifier.size(6.dp))
        Text(label, color = cs.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * A color field painted from one color: soft stacked blocks in its neighbors, the way a painter blocks in a canvas,
 * with a little grain. Different every day, the same all of one day.
 */
@Composable
fun ColorField(base: Color, modifier: Modifier = Modifier) {
    val day = remember { java.time.LocalDate.now().toEpochDay() }
    val f = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(android.graphics.Color.argb((base.alpha * 255).toInt(), (base.red * 255).toInt(), (base.green * 255).toInt(), (base.blue * 255).toInt()), f)
    val h = f[0]
    Canvas(modifier) {
        val r = Random(day)
        drawRect(hsl(h, 0.45f, 0.18f))
        val blocks = listOf(hsl(h - 28f, 0.55f, 0.42f), hsl(h + 18f, 0.50f, 0.58f), hsl(h + 160f, 0.35f, 0.62f), hsl(h, 0.60f, 0.32f))
        var y = size.height * 0.06f
        blocks.forEachIndexed { i, c ->
            val bh = size.height * (0.16f + r.nextFloat() * 0.14f)
            val inset = size.width * (0.07f + r.nextFloat() * 0.05f)
            // Feathered edges: the same block a few times, each a little larger and fainter.
            for (k in 6 downTo 0) {
                val grow = k * 5f
                drawRoundRect(c.copy(alpha = if (k == 0) 0.92f else 0.05f), Offset(inset - grow, y - grow), Size(size.width - 2 * inset + 2 * grow, bh + 2 * grow), CornerRadius(18f + grow))
            }
            y += bh + size.height * (0.04f + r.nextFloat() * 0.03f)
            if (i == 2 && y > size.height * 0.8f) return@forEachIndexed
        }
        repeat(900) { drawCircle(Color.White.copy(alpha = 0.035f), 1.2f, Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)) }
    }
}
