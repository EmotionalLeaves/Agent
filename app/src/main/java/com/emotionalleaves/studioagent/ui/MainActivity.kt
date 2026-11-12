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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
data class Incoming(val route: String, val text: String = "", val image: Uri? = null)

object Shared {
    var text: String = ""
    var image: Uri? = null
}

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Incoming?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { StudioTheme { Root(incoming) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    @Suppress("DEPRECATION")
    private fun handle(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND) {
            val stream: Uri? = try { i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } catch (e: Exception) { null }
            incoming.value = Incoming("share", i.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), stream)
            return
        }
        val route = i.getStringExtra("route") ?: return
        if (route != "startnow") { incoming.value = Incoming(route); return }
        // The tile and the widget start the clock straight away: no screen of choices first.
        if (!st.onboarded) { incoming.value = Incoming("onboarding"); return }
        lifecycleScope.launch {
            if (st.sessionStart == 0L) {
                try { withContext(Dispatchers.IO) { Engine.quickStart(st.defaultSessionMin) } } catch (e: Exception) { }
                if (st.dndInSession) Notifier.dnd(this@MainActivity, true)
            }
            incoming.value = Incoming("session")
        }
    }
}

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    val green = Color(0xFF2F5D50)
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF9BD1BF), secondary = Color(0xFFE2B48C))
    else lightColorScheme(
        primary = green, secondary = Color(0xFFB5622B), primaryContainer = Color(0xFFD6E9E2), onPrimaryContainer = Color(0xFF10261F),
        background = Color(0xFFFAF9F6), surface = Color(0xFFFAF9F6), surfaceVariant = Color(0xFFECE9E2)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun NavBackStackEntry.id(): Long = arguments?.getString("id")?.toLongOrNull() ?: 0L

@Composable
fun Root(incoming: MutableState<Incoming?>) {
    val nav = rememberNavController()
    val inc = incoming.value
    LaunchedEffect(inc) {
        if (inc != null) {
            if (inc.route == "share") { Shared.text = inc.text; Shared.image = inc.image }
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
    // Simple layout keeps the bar to four things. Calls and People are still one tap away under More.
    val tabs = if (tick >= 0 && st.simpleMode) listOf(
        Triple("home", "Home", Icons.Default.Home), Triple("works", "Studio", Icons.Default.Create),
        Triple("guide", "Guide", Icons.Default.Info), Triple("more", "More", Icons.Default.Menu)
    ) else listOf(
        Triple("home", "Home", Icons.Default.Home), Triple("works", "Studio", Icons.Default.Create), Triple("calls", "Calls", Icons.Default.Search),
        Triple("outreach", "People", Icons.Default.Person), Triple("more", "More", Icons.Default.Menu)
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { nav.navigate(r) { popUpTo("home"); launchSingleTop = true } },
                        icon = { Icon(icon, label) }, label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { Home(nav) }
            composable("onboarding") { Onboarding(nav) }
            composable("session") { SessionScreen(nav) }
            composable("admin") { Admin(nav) }
            composable("share") { ShareScreen(nav) }
            composable("works") { Works(nav) }
            composable("work/{id}") { WorkDetail(nav, it.id()) }
            composable("crop/{id}") { CropScreen(nav, it.id()) }
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
            composable("contact/{id}") { ContactScreen(nav, it.id()) }
            composable("records") { Records(nav) }
            composable("settings") { SettingsScreen(nav, "") }
            composable("settings/{section}") { SettingsScreen(nav, it.arguments?.getString("section").orEmpty()) }
            composable("guide") { Guide(nav) }
            composable("done") { DoneScreen(nav) }
            composable("receipts") { Receipts(nav) }
            composable("week") { WeekScreen(nav) }
            composable("sessions") { SessionsScreen(nav) }
            composable("more") { More(nav) }
        }
    }
}
