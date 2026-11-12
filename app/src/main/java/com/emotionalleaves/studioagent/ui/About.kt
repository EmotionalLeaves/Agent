package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController

/** What the app is, where your things are kept, and who made the parts it is built from. */
@Composable
fun About(nav: NavController) {
    val ctx = LocalContext.current
    val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "" }
    Screen("About this app", nav) {
        Text("Studio Agent" + (if (!version.isNullOrBlank()) " $version" else ""), style = MaterialTheme.typography.titleMedium)
        Text("Gets your art into shows by doing the paperwork, the searching and the reminders, so your time goes to the work.", style = MaterialTheme.typography.bodyMedium)
        Section("Where your things are kept")
        Text("Your pieces, pictures, writing and records stay on this phone. Nothing leaves it unless you set up one of these, and then only that part:", style = MaterialTheme.typography.bodyMedium)
        Text("• Email: applications and letters you approve go out from your own account.", style = MaterialTheme.typography.bodyMedium)
        Text("• AI writing help: the text being written is sent to the AI service whose key you added.", style = MaterialTheme.typography.bodyMedium)
        Text("• GitHub: your backup and portfolio page go to your own account.", style = MaterialTheme.typography.bodyMedium)
        Text("There are no ads, no tracking and no account with us.", style = MaterialTheme.typography.bodyMedium)
        Section("Your rights")
        Text("Picture files the app exports carry your name and a copyright line inside them. Certificates and bills of sale say that you keep the copyright when a work is sold.", style = MaterialTheme.typography.bodyMedium)
        Section("Typefaces")
        Text("Bricolage Grotesque by Mathieu Triay, and Atkinson Hyperlegible by the Braille Institute (shown here as Studio Legible, with a plain zero). Both are used under the SIL Open Font License 1.1.", style = MaterialTheme.typography.bodyMedium)
    }
}
