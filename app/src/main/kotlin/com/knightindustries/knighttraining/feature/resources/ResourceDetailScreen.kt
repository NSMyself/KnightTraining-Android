package com.knightindustries.knighttraining.feature.resources

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.kindNameRes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One shared, kind-aware detail screen (iOS consolidated its progress screens the same way).
 * Articles get "Read article" and a read toggle; everything else a progress slider and "Mark as
 * finished". No "open course" button on progress kinds: an untracked open next to "Time invested"
 * would undercount it (iOS's call); the link stays reachable through Share and Edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDetailScreen(viewModel: ResourceDetailViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val resource = viewModel.resource
    var menuOpen by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel.isDeleted) { if (viewModel.isDeleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) } },
                actions = {
                    resource?.let { current ->
                        IconButton(onClick = { share(context, current) }) { Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share)) }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menuOpen = false; editing = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; confirmingDelete = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = resource ?: return@Scaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ResourceCover(current, Modifier.width(if (current.kind.code == ResourceKind.BOOK || current.kind.code == ResourceKind.COURSE) 120.dp else 180.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                    current.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                    Text(stringResource(kindNameRes(current.kind.code)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOfNotNull(current.edition, current.publisher).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current.kind.supportsOwnership && current.isOwned) AssistChip(onClick = {}, label = { Text(stringResource(R.string.owned)) })
                        if (current.isAutoImported) AssistChip(onClick = {}, label = { Text(stringResource(R.string.auto_imported)) })
                    }
                }
            }

            Stats(current, viewModel.timeInvestedSeconds)

            if (current.kind.code == ResourceKind.ARTICLE) {
                current.url?.let { url ->
                    Button(onClick = { openLink(context, url) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        Text(stringResource(R.string.read_article), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedButton(onClick = { if (current.isCompleted) viewModel.restart() else viewModel.markFinished(null, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (current.isCompleted) R.string.mark_unread else R.string.mark_read))
                }
            } else {
                ProgressSlider(current.progressPercentage, viewModel::setProgress)
                if (current.isCompleted) {
                    OutlinedButton(onClick = viewModel::restart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_again)) }
                } else {
                    Button(onClick = { finishing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mark_finished)) }
                }
            }

            current.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            NotesField(current.notes.orEmpty(), viewModel::saveNotes)

            if (viewModel.completions.isNotEmpty()) {
                Text(stringResource(R.string.completion_history), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                val pattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMd") }
                viewModel.completions.forEach { completion ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.finished_on, completion.completedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))), style = MaterialTheme.typography.labelLarge)
                            completion.rating?.let { Stars(it) }
                            completion.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }

    if (finishing) FinishDialog(onDismiss = { finishing = false }) { notes, rating -> viewModel.markFinished(notes, rating); finishing = false }
    if (editing) resource?.let { EditResourceSheet(it, onDismiss = { editing = false }) { updated -> viewModel.saveDetails(updated); editing = false } }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_resource_title)) },
            text = { Text(stringResource(R.string.delete_resource_body)) },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; viewModel.delete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Stats(resource: Resource, timeInvested: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            stringResource(R.string.progress_label) to "${resource.progressPercentage}%",
            stringResource(R.string.time_invested) to durationText(timeInvested),
            stringResource(R.string.completions) to resource.completionCount.toString(),
        ).forEach { (label, value) ->
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(value, style = MaterialTheme.typography.titleMedium)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Saves when released, not on every step of the drag. */
@Composable
private fun ProgressSlider(progress: Int, onSet: (Int) -> Unit) {
    var value by remember(progress) { mutableFloatStateOf(progress.toFloat()) }
    Column {
        Text("${stringResource(R.string.progress_label)}: ${value.toInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = { value = it }, valueRange = 0f..100f, onValueChangeFinished = { onSet(value.toInt()) })
    }
}

@Composable
private fun NotesField(initial: String, onSave: (String) -> Unit) {
    var notes by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 3,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onSave(notes) },
    )
}

@Composable
private fun Stars(rating: Int, onRate: ((Int) -> Unit)? = null) {
    Row {
        (1..5).forEach { star ->
            val icon = if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder
            if (onRate == null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            } else {
                IconButton(onClick = { onRate(star) }) { Icon(icon, contentDescription = "$star", tint = MaterialTheme.colorScheme.tertiary) }
            }
        }
    }
}

@Composable
private fun FinishDialog(onDismiss: () -> Unit, onFinish: (String?, Int?) -> Unit) {
    var notes by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.finish_rating), style = MaterialTheme.typography.labelLarge)
                Stars(rating) { rating = it }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.finish_notes)) }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onFinish(notes.trim().ifEmpty { null }, rating.takeIf { it > 0 }) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun share(context: android.content.Context, resource: Resource) {
    val text = listOfNotNull(resource.title, resource.author, resource.url).joinToString("\n")
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}
