package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.domain.Feedback
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Asking a few people which pieces are strongest. Numbered pictures and three short questions go out by
 * text or email; what they say comes back here, is kept with the pieces, and can set the portfolio order.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(nav: NavController) {
    val dao = G.dao; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val works by dao.artworks().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    val pickable = works.filter { it.imagePath.isNotBlank() && it.status != "idea" }
    val chosen = remember(tick, works) { st.feedbackIds.split(",").mapNotNull { it.toLongOrNull() }.filter { id -> pickable.any { it.id == id } } }
    val questions = remember(tick) { st.feedbackQuestions.split("\n").filter { it.isNotBlank() }.ifEmpty { Feedback.defaultQuestions } }
    var status by remember { mutableStateOf("") }
    Screen("Ask for feedback", nav) {
        Intro("feedback", "Three people's honest pick tells you more than a week of wondering. Choose a few pieces, send them numbered with three short questions, and note what each person says.")
        if (pickable.size < 2) { Note("Add pictures of at least two pieces in Studio, and you can ask people which is strongest."); return@Screen }
        Section("1. The pieces (three to six is best)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pickable.forEach { w -> FilterChip(selected = w.id in chosen, onClick = {
                val next = if (w.id in chosen) chosen - w.id else (chosen + w.id).take(8)
                st.feedbackIds = next.joinToString(",")
            }, label = { Text(w.title.ifBlank { "Untitled #${w.id}" }) }) }
        }
        Section("2. The questions")
        questions.forEachIndexed { i, q -> Field("Question ${i + 1}", q, { t -> st.feedbackQuestions = questions.toMutableList().also { it[i] = t }.joinToString("\n") }, lines = 2) }
        Section("3. Send")
        Button(enabled = chosen.size >= 2, onClick = {
            scope.io({ status = it }) {
                val dir = File(ImageTools.exportDir(ctx), "feedback").apply { deleteRecursively(); mkdirs() }
                val ws = chosen.mapNotNull { id -> works.firstOrNull { it.id == id } }
                val files = ws.mapIndexed { i, w -> File(dir, "${i + 1}_" + w.title.filter { it.isLetterOrDigit() }.take(24).ifBlank { "piece" } + ".jpg").also { ImageTools.export(w.imagePath, 1600, 2L * 1024 * 1024, it) } }
                val text = Feedback.message(st.firstName, ws.map { it.title.ifBlank { "Untitled" } }, questions)
                st.feedbackAskedAt = System.currentTimeMillis()
                status = "The message is copied too, in case the app takes only the pictures."
                withContext(Dispatchers.Main) { Share.files(ctx, files, "image/jpeg", text) }
            }
        }) { Text(if (chosen.size < 2) "Choose at least two pieces" else "Send ${chosen.size} pictures with the questions") }
        Small("Send it to two or three people whose eye you trust: a friend who goes to shows, a coworker, a teacher. Not everyone has to be an artist.")
        if (status.isNotBlank()) Note(status)

        if (chosen.isNotEmpty()) {
            Section("4. What they said")
            val titles = chosen.associateWith { id -> works.firstOrNull { it.id == id }?.title?.ifBlank { "Untitled" } ?: "" }
            Feedback.summary(chosen, titles.mapValues { "No. ${chosen.indexOf(it.key) + 1}, ${it.value}," }, st.feedbackVotes).takeIf { it.isNotBlank() }?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            var who by rememberSaveable { mutableStateOf("") }
            var pick by rememberSaveable { mutableStateOf("") }
            var said by rememberSaveable { mutableStateOf("") }
            Field("Who", who, { who = it })
            Small("Their pick:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chosen.forEachIndexed { i, id -> FilterChip(selected = pick == id.toString(), onClick = { pick = if (pick == id.toString()) "" else id.toString() }, label = { Text("${i + 1}. ${titles[id]}") }) }
            }
            Field("What they said (a few words is plenty)", said, { said = it }, lines = 3, mic = true)
            if (pick.isNotBlank() || said.isNotBlank()) Button(onClick = {
                val p = pick.toLongOrNull(); val words = said.trim(); val name = who.trim().ifBlank { "someone" }
                if (p != null) st.feedbackVotes = Feedback.addVote(st.feedbackVotes, p)
                scope.io {
                    // Kept with each piece it was about, dated, so it is there the next time you open the piece.
                    if (words.isNotBlank()) chosen.forEach { id -> dao.artworksNow().firstOrNull { it.id == id }?.let { w -> dao.put(w.copy(notes = listOf(w.notes, "Feedback from $name, ${java.time.LocalDate.now()}: $words").filter { it.isNotBlank() }.joinToString("\n"))) } }
                }
                who = ""; pick = ""; said = ""; status = "Kept. Thank them: it was a kindness."
            }) { Text("Keep this answer") }
            val votes = Feedback.votes(st.feedbackVotes)
            if (votes.filterKeys { it in chosen }.values.sum() >= 2) TextButton(onClick = {
                val all = Engine.portfolioOrdered(works).map { it.id }
                val top = Feedback.byVotes(chosen.filter { v -> (votes[v] ?: 0) > 0 }, st.feedbackVotes)
                st.portfolioOrder = (top + all.filter { it !in top }).joinToString(",")
                status = "Your portfolio now opens with the pieces people picked, most picked first."
            }) { Text("Open my portfolio with the pieces they picked") }
            if (votes.isNotEmpty()) TextButton(onClick = { st.feedbackVotes = ""; status = "Picks cleared, for a new round of asking." }) { Text("Start a new round") }
        }
    }
}
