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

/** One palette everywhere: warm paper, deep green, a little terracotta. No stock purple. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D50), onPrimary = Color.White, primaryContainer = Color(0xFFD9EAE3), onPrimaryContainer = Color(0xFF10261F),
    secondary = Color(0xFFA9571F), onSecondary = Color.White, secondaryContainer = Color(0xFFDCEBE5), onSecondaryContainer = Color(0xFF16332B),
    tertiary = Color(0xFF7A5C2E), onTertiary = Color.White, tertiaryContainer = Color(0xFFF3E4C8), onTertiaryContainer = Color(0xFF2B1F08),
    background = Color(0xFFFAF9F6), onBackground = Color(0xFF1D1C1A), surface = Color(0xFFFAF9F6), onSurface = Color(0xFF1D1C1A),
    surfaceVariant = Color(0xFFECE9E2), onSurfaceVariant = Color(0xFF55524B), outline = Color(0xFF8A867D), outlineVariant = Color(0xFFDAD6CD),
    surfaceTint = Color(0xFF2F5D50), inverseSurface = Color(0xFF32302C), inverseOnSurface = Color(0xFFF5F0E8), inversePrimary = Color(0xFF9BD1BF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF6F4EF), surfaceContainer = Color(0xFFF1EFE9),
    surfaceContainerHigh = Color(0xFFECE9E2), surfaceContainerHighest = Color(0xFFE7E4DC), surfaceBright = Color(0xFFFAF9F6), surfaceDim = Color(0xFFDDDAD2)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD1BF), onPrimary = Color(0xFF0B3128), primaryContainer = Color(0xFF214A3F), onPrimaryContainer = Color(0xFFD9EAE3),
    secondary = Color(0xFFE8B48A), onSecondary = Color(0xFF4A2A10), secondaryContainer = Color(0xFF2A3B35), onSecondaryContainer = Color(0xFFD9EAE3),
    tertiary = Color(0xFFDDC28F), onTertiary = Color(0xFF3D2E0C), tertiaryContainer = Color(0xFF554420), onTertiaryContainer = Color(0xFFF3E4C8),
    background = Color(0xFF151513), onBackground = Color(0xFFE8E4DC), surface = Color(0xFF151513), onSurface = Color(0xFFE8E4DC),
    surfaceVariant = Color(0xFF3A3833), onSurfaceVariant = Color(0xFFC9C5BB), outline = Color(0xFF938F86), outlineVariant = Color(0xFF45433D),
    surfaceTint = Color(0xFF9BD1BF), inverseSurface = Color(0xFFE8E4DC), inverseOnSurface = Color(0xFF32302C), inversePrimary = Color(0xFF2F5D50),
    surfaceContainerLowest = Color(0xFF0F0F0E), surfaceContainerLow = Color(0xFF1C1C1A), surfaceContainer = Color(0xFF21211E),
    surfaceContainerHigh = Color(0xFF2B2B27), surfaceContainerHighest = Color(0xFF363631), surfaceBright = Color(0xFF3B3A36), surfaceDim = Color(0xFF151513)
)

/** Slightly smaller, tighter text than the stock scale, with clear steps between heading, body and note. */
private val AppType = Typography().let { t ->
    t.copy(
        displayMedium = t.displayMedium.copy(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        headlineSmall = t.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleLarge = t.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodySmall = t.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
        labelLarge = t.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.1.sp),
        labelMedium = t.labelMedium.copy(letterSpacing = 0.8.sp)
    )
}

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, typography = AppType, content = content)
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
