package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.domain.Drafts
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The show's news, written for listings, and the places it goes. Each place ticked as you send it. */
@Composable
fun AnnounceScreen(nav: NavController, applId: Long) {
    val ctx = LocalContext.current
    var text by rememberSaveable(applId) { mutableStateOf("") }
    LaunchedEffect(applId) { if (text.isBlank()) text = withContext(Dispatchers.IO) { Engine.announcement(com.emotionalleaves.studioagent.G.dao.applsNow().firstOrNull { it.id == applId } ?: return@withContext "") } }
    val tick by st.changed.collectAsState()
    Screen("Tell people about the show", nav) {
        Intro("announce", "Newspaper calendars, radio, the venue and boards around town list small shows for free. The announcement is written from your show's details and your bio; change anything, then send it to each place.")
        Field("The announcement", text, { text = it }, lines = 10)
        Button(onClick = { Share.text(ctx, text) }) { Text("Send or copy it") }
        Section("Where it goes")
        Drafts.listingPlaces.forEachIndexed { i, (place, how) ->
            val key = "$applId:$i"
            Toggle(place, tick >= 0 && key in st.announced.split(",")) { on -> st.announced = (st.announced.split(",").filter { it.isNotBlank() && it != key } + (if (on) listOf(key) else emptyList())).takeLast(300).joinToString(",") }
            Small(how)
        }
        Note("Send it two to three weeks before the opening. A good photo of one piece, attached, makes a listing far more likely to run.")
    }
}
