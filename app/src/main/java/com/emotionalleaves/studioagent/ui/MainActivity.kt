package com.emotionalleaves.studioagent.ui

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
        "home", "session", "done", "admin", "week", "sessions", "review", "share" -> "home"
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
            if (route.substringBefore('/') !in setOf("session", "done", "crop", "share", "tour")) Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    tabs.forEach { (r, label, icon) ->
                        val on = tabFor(route, tabs.map { it.first }) == r
                        NavigationBarItem(
                            selected = on,
                            onClick = { nav.navigate(r) { popUpTo("home"); launchSingleTop = true } },
                            icon = { Icon(icon, null) }, label = {
                                // Tab names grow with the phone's text size, but only so far, so they never break mid-word.
                                val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
                                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = androidx.compose.ui.unit.TextUnit(13f * minOf(fs, 1.3f) / fs, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1, softWrap = false)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
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
            composable("foundations") { FoundationsScreen(nav) }
            composable("market") { MarketDay(nav) }
            composable("pricing") { PricingScreen(nav) }
            composable("findable") { FindableScreen(nav) }
            composable("tools") { ToolsScreen(nav) }
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
