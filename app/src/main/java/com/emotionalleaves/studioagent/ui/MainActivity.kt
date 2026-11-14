package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.emotionalleaves.studioagent.sys.Notifier
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What another app (or a notification, tile or widget) handed us. */
data class Incoming(val route: String, val text: String = "", val image: Uri? = null, val images: List<Uri> = emptyList())

object Shared {
    var text: String = ""
    var image: Uri? = null
    /** Several pictures shared at once, usually your own work from the gallery. */
    var images: List<Uri> = emptyList()
}

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Incoming?>(null)

    override fun onResume() {
        super.onResume()
        // A long gap since the app was last opened: Today greets it kindly instead of showing the backlog first.
        val now = System.currentTimeMillis(); val last = st.lastOpened
        if (last > 0 && now - last >= 7 * 86_400_000L) st.awayDays = ((now - last) / 86_400_000L).toInt()
        st.lastOpened = now
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        // A brand-new install opens on the tour, unless a share, widget or notification asked for a screen.
        if (savedInstanceState == null && incoming.value == null && !st.tourSeen && !st.onboarded) incoming.value = Incoming("tour")
        setContent { StudioTheme { Root(incoming) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    @Suppress("DEPRECATION")
    private fun handle(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND_MULTIPLE) {
            val list: List<Uri> = try { i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty() } catch (e: Exception) { emptyList() }
            incoming.value = Incoming("share", "", list.firstOrNull(), list)
            return
        }
        if (i.action == Intent.ACTION_SEND) {
            val stream: Uri? = try { i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } catch (e: Exception) { null }
            incoming.value = Incoming("share", i.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), stream)
            return
        }
        val route = i.getStringExtra("route") ?: return
        if (route != "startnow") { incoming.value = Incoming(route); return }
        // The tile and the widget start the clock straight away: no screen of choices first.
        if (!st.onboarded) { incoming.value = Incoming("tour"); return }
        lifecycleScope.launch {
            if (st.sessionStart == 0L) {
                try { withContext(Dispatchers.IO) { Engine.quickStart(st.defaultSessionMin) } } catch (e: Exception) { }
                if (st.dndInSession) Notifier.dnd(this@MainActivity, true)
            }
            incoming.value = Incoming("session")
        }
    }
}

/** Which bottom tab a screen belongs to, so the bar always shows where you are, even a few taps deep. */
fun tabFor(route: String, tabs: List<String>): String {
    val head = route.substringBefore('/')
    val home = when (head) {
        "home", "session", "done", "admin", "week", "sessions", "review", "share", "dump" -> "home"
        "works", "work", "crop", "ideas" -> "works"
        "calls", "call", "appl", "web", "sources" -> "calls"
        "outreach", "contact", "letters" -> "more"
        else -> "more"
    }
    return if (home in tabs) home else "more"
}

private fun NavBackStackEntry.id(): Long = arguments?.getString("id")?.toLongOrNull() ?: 0L

@Composable
fun Root(incoming: MutableState<Incoming?>) {
    val nav = rememberNavController()
    val inc = incoming.value
    LaunchedEffect(inc) {
        if (inc != null) {
            if (inc.route == "share") { Shared.text = inc.text; Shared.image = inc.image; Shared.images = inc.images }
            // The screens are set up a moment after this runs on a cold start, so wait for them before moving.
            var tries = 0
            while (tries < 200) {
                val ready = try { nav.graph; true } catch (e: IllegalStateException) { false }
                if (ready) break
                withFrameNanos { }
                tries++
            }
            try { nav.navigate(inc.route) { launchSingleTop = true } } catch (e: Exception) { }
            incoming.value = null
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val tick by st.changed.collectAsState()
    // Four places, always the same, each named for what is in it.
    val tabs = listOf(
        Triple("home", "Today", Icons.Default.Home), Triple("works", "Studio", Icons.Default.Create), Triple("calls", "Calls", Icons.Default.Search),
        Triple("more", "You", Icons.Default.AccountCircle)
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Sessions, setup and the saved screen get the whole phone: nothing else to tap.
            if (route.substringBefore('/') !in setOf("session", "done", "crop", "share", "tour", "wall")) {
                // A floating bar of ink with the tab you are on lit in your own color: always four, always labelled.
                val cs = MaterialTheme.colorScheme
                val dark = isDark()
                val bar = if (dark) cs.surfaceContainerHigh else cs.inverseSurface
                val pill = if (dark) cs.primary else cs.inversePrimary
                val onPill = if (dark) cs.onPrimary else cs.inverseSurface
                val idle = if (dark) cs.onSurfaceVariant else cs.inverseOnSurface.copy(alpha = 0.72f)
                Box(Modifier.fillMaxWidth().background(cs.background).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(bar).padding(6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tabs.forEach { (r, label, icon) ->
                            val on = tabFor(route, tabs.map { it.first }) == r
                            val w by androidx.compose.animation.core.animateFloatAsState(if (on) 1.35f else 1f, tween(220), label = "tab")
                            Column(Modifier.weight(w).clip(RoundedCornerShape(24.dp)).background(if (on) pill else Color.Transparent)
                                .selectable(selected = on, role = Role.Tab) { nav.navigate(r) { popUpTo("home"); launchSingleTop = true } }
                                .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(icon, null, Modifier.size(22.dp), tint = if (on) onPill else idle)
                                // Tab names grow with the phone's text size, but only so far, so they never break mid-word.
                                val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
                                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) onPill else idle,
                                    fontSize = androidx.compose.ui.unit.TextUnit(13f * minOf(fs, 1.3f) / fs, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(pad),
            // A short, quiet move: forward slides in a little from the right, back slides away to it.
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 14 } },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 14 } }
        ) {
            composable("home") { Home(nav) }
            composable("tour") { Tour(nav) }
            composable("wall") { ShowWall(nav) }
            composable("about") { About(nav) }
            composable("session") { SessionScreen(nav) }
            composable("admin") { Admin(nav) }
            composable("share") { ShareScreen(nav) }
            composable("works") { Works(nav) }
            composable("work/{id}") { WorkDetail(nav, it.id()) }
            composable("crop/{id}") { CropScreen(nav, it.id()) }
            composable("look/{id}") { LookScreen(nav, it.id()) }
            composable("feedback") { FeedbackScreen(nav) }
            composable("commission") { CommissionStart(nav) }
            composable("year") { YearScreen(nav) }
            composable("check") { OfferCheck(nav) }
            composable("dump") { DumpScreen(nav) }
            composable("teach") { TeachScreen(nav) }
            composable("people") { PeopleScreen(nav) }
            composable("foundations") { FoundationsScreen(nav) }
            composable("market") { MarketDay(nav) }
            composable("pricing") { PricingScreen(nav) }
            composable("findable") { FindableScreen(nav) }
            composable("tools") { ToolsScreen(nav) }
            composable("health") { HealthScreen(nav) }
            composable("photoshoot") { PhotoshootScreen(nav) }
            composable("supplies") { SuppliesScreen(nav) }
            composable("glossary") { GlossaryScreen(nav) }
            composable("decide") { DecideScreen(nav) }
            composable("announce/{id}") { AnnounceScreen(nav, it.id()) }
            composable("ideas") { Ideas(nav) }
            composable("materials") { Materials(nav) }
            composable("interview") { Interview(nav) }
            composable("doc/{id}") { DocScreen(nav, it.id()) }
            composable("facts") { Facts(nav) }
            composable("calls") { Calls(nav) }
            composable("call/{id}") { CallDetail(nav, it.id()) }
            composable("appl/{id}") { ApplScreen(nav, it.id()) }
            composable("web/{id}") { WebFill(nav, it.id()) }
            composable("sources") { Sources(nav) }
            composable("outreach") { Outreach(nav) }
            composable("nearby") { Nearby(nav) }
            composable("nearby/labs") { Nearby(nav, startWithLabs = true) }
            composable("update") { UpdateScreen(nav) }
            composable("order") { PortfolioOrder(nav) }
            composable("search") { SearchScreen(nav) }
            composable("opening") { AtTheOpening(nav) }
            composable("plan/{id}") { PlanShow(nav, it.id()) }
            composable("jury/{id}") { JuryView(nav, it.id()) }
            composable("contact/{id}") { ContactScreen(nav, it.id()) }
            composable("records") { Records(nav) }
            composable("settings") { SettingsScreen(nav, "") }
            composable("settings/{section}") { SettingsScreen(nav, it.arguments?.getString("section").orEmpty()) }
            composable("guide") { Guide(nav) }
            composable("done") { DoneScreen(nav) }
            composable("basics") { Basics(nav) }
            composable("letters") { Letters(nav) }
            composable("path") { PathScreen(nav) }
            composable("review") { Review(nav) }
            composable("receipts") { Receipts(nav) }
            composable("week") { WeekScreen(nav) }
            composable("sessions") { SessionsScreen(nav) }
            composable("more") { More(nav) }
        }
    }
}
