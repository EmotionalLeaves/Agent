package com.emotionalleaves.studioagent.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

/**
 * The first thing a new install shows: how the app works, one idea per page, with a picture of the real screen.
 * It ends by starting the app straight away. Nothing has to be filled in; a name is optional.
 * Open it again any time from More, The tour.
 */
private class TourPage(val title: String, val text: String, val picture: @Composable () -> Unit)

@Composable
fun Tour(nav: NavController) {
    val scope = rememberCoroutineScope()
    val name = remember { mutableStateOf(st.firstName) }
    val pages = listOf(
        TourPage("From the studio to a wall", "You make the art. This app does nearly everything else: time, records, writing, finding shows, applying, follow-up.") { PicWall() },
        TourPage("One step at a time", "Home shows a single next step, pinned up like a label. Not today? Put it off with one tap. Nothing is ever marked missed.") { PicLabel() },
        TourPage("Tap Start, then make", "One tap starts a timer on the right piece. The screen shows only the small step you are on. Stop whenever; it is saved.") { PicTimer() },
        TourPage("Your work, on a wall", "Each piece gets a picture and a short label. When it is finished, the app fills in sizes and file details for you.") { PicTiles() },
        TourPage("Shows come to you", "Once a few pieces are done, the app sorts open calls by how well they fit, and only shows ones right for where you are.") { PicCall() },
        TourPage("Nothing happens without your yes", "Every application and email waits for you to approve it. Then it is done, with a receipt, so there is nothing to check twice.") { PicCalm() },
        TourPage("Ready", "That is all you need. There is nothing to set up first.") { PicReady() }
    )
    val pager = rememberPagerState { pages.size }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finishTour(nav) }
    fun start() {
        if (name.value.isNotBlank()) st.firstName = name.value.trim()
        st.setupDone = true; st.tourSeen = true
        if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS) else finishTour(nav)
    }
    val buzz = rememberBuzz()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${pager.currentPage + 1} of ${pages.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!last) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.lastIndex) } }) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 250.dp).padding(top = 8.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) { p.picture() }
                Text(p.title, style = MaterialTheme.typography.headlineLarge)
                Text(p.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (i == pages.lastIndex) {
                    Field("What should it call you? (optional)", name)
                    Small("You can add more about yourself later, only if a feature needs it.")
                }
            }
        }
        // Where you are in the tour, as a row of short bars.
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).semantics { contentDescription = "Page ${pager.currentPage + 1} of ${pages.size}" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pages.indices.forEach { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i <= pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh))
            }
        }
        if (last) {
            Button(onClick = { buzz(); start() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start using it") }
            TextButton(onClick = { st.setupDone = true; st.tourSeen = true; if (name.value.isNotBlank()) st.firstName = name.value.trim(); nav.navigate("onboarding") { popUpTo("home") } }, modifier = Modifier.fillMaxWidth()) { Text("Set my week and hours first") }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text("Next") }
        }
    }
}

private fun finishTour(nav: NavController) {
    nav.navigate("home") { popUpTo("home") { inclusive = true } }
}

/* Small drawings of the real screens. They are pictures only: not tappable and not read aloud. */

@Composable
private fun PicWall() {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(170.dp).aspectRatio(0.8f).border(7.dp, cs.onSurface, RoundedCornerShape(2.dp)).padding(7.dp).background(cs.surfaceContainerLowest).padding(18.dp)) {
            Box(Modifier.fillMaxSize().background(cs.secondaryContainer))
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(cs.primary, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.68f, size.height * 0.30f))
                drawLine(cs.primary, Offset(0f, size.height * 0.78f), Offset(size.width, size.height * 0.56f), 6.dp.toPx(), StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.width(120.dp).background(cs.surfaceContainerLowest).padding(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(7.dp).background(cs.onSurface))
                Box(Modifier.fillMaxWidth(0.5f).height(5.dp).background(cs.outline))
            }
        }
    }
}

@Composable
private fun PicLabel() {
    Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WallLabel(onClick = null) {
            Text("Block in the big shapes", style = MaterialTheme.typography.headlineSmall)
            Small("Two-minute start: open the file.")
        }
        FakeButton("Start now")
    }
}

@Composable
private fun PicTimer() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 10.dp.toPx(); val inset = w / 2
            drawArc(cs.surfaceContainerHigh, 0f, 360f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w))
            drawArc(cs.primary, -90f, 115f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("12:40", style = MaterialTheme.typography.displayMedium)
            Small("27 min to go")
        }
    }
}

@Composable
private fun PicTiles() {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.width(280.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("Night Shift" to "In progress", "Walk-in Cooler" to "Ready to send").forEachIndexed { i, (t, s) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(3.dp)).background(cs.surfaceContainerLowest).padding(12.dp)) {
                    Box(Modifier.fillMaxSize().background(if (i == 0) cs.secondaryContainer else cs.primaryContainer))
                    if (i == 0) TapeStrip(Modifier.align(Alignment.TopCenter).offset(y = (-16).dp), 2f)
                }
                Text(t, style = MaterialTheme.typography.titleMedium)
                Text(s, style = MaterialTheme.typography.labelMedium, color = if (i == 0) cs.primary else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PicCall() {
    Box(Modifier.width(290.dp)) {
        Panel {
            Text("Winter Open: Small Works", style = MaterialTheme.typography.titleLarge)
            Small("Riverside Arts")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tag("Strong fit", strong = true); Tag("6 weekends left"); Tag("Free") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FakeButton("Yes", Modifier.width(80.dp)); FakeButton("No", Modifier.width(80.dp), outlined = true) }
        }
    }
}

@Composable
private fun PicCalm() {
    Box(Modifier.width(290.dp)) {
        Panel {
            Check(true, "Application sent, Tue 22 Dec, 9:14 am", bold = true)
            Hair()
            Check(true, "No streaks to break")
            Check(true, "Quiet during work and after 9 pm")
            Check(true, "Works without any AI key or account")
        }
    }
}

@Composable
private fun PicReady() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(150.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(70.dp)) {
            val s = 9.dp.toPx()
            drawLine(cs.onPrimaryContainer, Offset(size.width * 0.12f, size.height * 0.55f), Offset(size.width * 0.40f, size.height * 0.82f), s, StrokeCap.Round)
            drawLine(cs.onPrimaryContainer, Offset(size.width * 0.40f, size.height * 0.82f), Offset(size.width * 0.90f, size.height * 0.20f), s, StrokeCap.Round)
        }
    }
}

@Composable
private fun FakeButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), outlined: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).let { if (outlined) it.border(1.dp, cs.outline, RoundedCornerShape(12.dp)) else it.background(cs.primary) },
        contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (outlined) cs.primary else cs.onPrimary)
    }
}
