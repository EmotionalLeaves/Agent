package com.emotionalleaves.studioagent.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.media.ImageTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val dao get() = G.dao
val st get() = G.settings

fun now() = System.currentTimeMillis()

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
fun fmtDate(ms: Long): String = if (ms <= 0) "no date" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFmt)
fun money(d: Double): String = (if (d < 0) "-$" else "$") + "%.2f".format(kotlin.math.abs(d))

/** Run database or network work off the main thread; errors land in [onError] instead of crashing. */
fun CoroutineScope.io(onError: (String) -> Unit = {}, block: suspend () -> Unit) = launch(Dispatchers.IO) {
    try { block() } catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "Something went wrong") } }
}

@Composable
fun Screen(
    title: String, nav: NavController, back: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { if (back) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content
        )
    }
}

@Composable
fun H(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))

@Composable
fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Panel(onClick: (() -> Unit)? = null, strong: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = if (strong) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
    val m = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }
    Card(modifier = m, colors = colors) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
}

@Composable
fun Field(label: String, value: String, onValue: (String) -> Unit, lines: Int = 1, number: Boolean = false, secret: Boolean = false, mic: Boolean = false) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
            singleLine = lines == 1, minLines = lines,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (number) KeyboardType.Decimal else if (secret) KeyboardType.Password else KeyboardType.Text,
                capitalization = if (number || secret) KeyboardCapitalization.None else KeyboardCapitalization.Sentences
            ),
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
        )
        if (mic) MicButton { said -> onValue((value + " " + said).trim()) }
    }
}

/** A text field bound to a remembered string. */
@Composable
fun Field(label: String, state: MutableState<String>, lines: Int = 1, number: Boolean = false, mic: Boolean = false) =
    Field(label, state.value, { state.value = it }, lines, number, mic = mic)

@Composable
fun Choice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        if (label.isNotBlank()) Note(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o.replace('_', ' ')) }) }
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun MicButton(onText: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    TextButton(onClick = {
        try {
            launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        } catch (e: Exception) { }
    }) { Text("Speak instead") }
}

/** Shows an image file, decoded small and off the main thread. */
@Composable
fun PathImage(path: String, modifier: Modifier = Modifier, maxSide: Int = 700) {
    val stamp = if (path.isBlank()) 0L else File(path).lastModified()
    val bmp by produceState<ImageBitmap?>(null, path, stamp) {
        value = withContext(Dispatchers.IO) { ImageTools.decode(path, maxSide)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = ContentScale.Fit)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))

@Composable
fun Status(text: String) { if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
