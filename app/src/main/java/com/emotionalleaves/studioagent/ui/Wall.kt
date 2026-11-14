package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.work.Engine
import java.io.File

/**
 * Your work on a wall, one piece at a time: for showing someone on your phone at a market, an opening or at work.
 * Each piece hangs in a white frame on a quiet gallery wall with its museum label under it, in your portfolio order.
 * No tab bar, no buttons on the work: swipe for the next, the cross to leave.
 */
@Composable
fun ShowWall(nav: NavController) {
    val works by G.dao.artworks().collectAsState(emptyList())
    val hung = Engine.portfolioOrdered(works).filter { it.imagePath.isNotBlank() && File(it.imagePath).exists() }
    // The screen stays on while someone is looking.
    val window = (LocalContext.current as? android.app.Activity)?.window
    DisposableEffect(Unit) {
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val wall = Color(0xFFEDEBE6)
    Box(Modifier.fillMaxSize().background(wall)) {
        if (hung.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyFrame()
                Text("The wall is ready", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF1E1D1A))
                Text("Pieces marked Ready to send, with a picture, hang here in your portfolio order.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF55524C))
            }
        } else {
            val pager = rememberPagerState { hung.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { i -> Hung(hung[i]) }
            // Where you are in the wall, as small dots at the foot: the one on show filled.
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hung.indices.take(20).forEach { k ->
                    Box(Modifier.size(if (k == pager.currentPage) 9.dp else 6.dp).clip(CircleShape).background(if (k == pager.currentPage) Color(0xFF1E1D1A) else Color(0x551E1D1A)))
                }
            }
        }
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Default.Close, "Leave the wall", tint = Color(0xFF1E1D1A))
        }
    }
}

/** One piece: a white frame with a soft shadow, and the label a gallery would print, beside it on the wall. */
@Composable
private fun Hung(w: Artwork) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp, vertical = 56.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f, fill = false).shadow(14.dp, RoundedCornerShape(2.dp)).background(Color.White).padding(14.dp)) {
            PathImage(w.imagePath, Modifier.widthIn(max = 520.dp), 1400, ContentScale.Fit, w.title.ifBlank { "Untitled" })
        }
        Column(Modifier.padding(top = 30.dp).widthIn(max = 320.dp).fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val ink = Color(0xFF1E1D1A); val pencil = Color(0xFF55524C)
            if (st.fullName.isNotBlank()) Text(st.fullName, style = MaterialTheme.typography.labelLarge, color = pencil)
            Text(w.title.ifBlank { "Untitled" } + (if (w.year > 0) ", ${w.year}" else ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink)
            val made = listOf(w.medium, w.support).filter { it.isNotBlank() }.joinToString(" on ")
            if (made.isNotBlank()) Text(made, style = MaterialTheme.typography.bodyMedium, color = pencil)
            if (w.widthIn > 0 && w.heightIn > 0) Text(Texts.dims(w.widthIn, w.heightIn, w.depthIn), style = MaterialTheme.typography.bodySmall, color = pencil)
            // Sold gets the gallery's red dot; not for sale says so; otherwise the price.
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    w.status == "sold" -> { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD32F2F))); Text("Sold", style = MaterialTheme.typography.bodySmall, color = ink) }
                    w.nfs -> Text("Not for sale", style = MaterialTheme.typography.bodySmall, color = pencil)
                    w.price > 0 -> Text(money(w.price), style = MaterialTheme.typography.bodyMedium, color = ink)
                }
            }
        }
    }
}
