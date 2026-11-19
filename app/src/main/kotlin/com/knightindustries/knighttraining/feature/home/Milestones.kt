package com.knightindustries.knighttraining.feature.home

import androidx.compose.animation.animateContentSize
import com.knightindustries.knighttraining.ui.AnimatedLinearProgress
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.Milestone
import com.knightindustries.knighttraining.core.model.MilestonesSnapshot
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.ui.ratingTypeNameRes
import com.knightindustries.knighttraining.ui.ratingTypeOrder

/** Derived at display time, never stored, so it follows the app's language (iOS `displayTitle`). */
@Composable
fun milestoneTitle(milestone: Milestone): String = when (milestone.kind) {
    Milestone.ELO -> {
        val type = milestone.ratingType
        val target = milestone.targetRating
        if (type != null && target != null) stringResource(R.string.milestone_target_elo, target, stringResource(ratingTypeNameRes(type))) else milestone.text.orEmpty()
    }
    // Title codes (GM, IM, …) are shown verbatim, never translated.
    Milestone.TITLE -> milestone.targetTitle?.let { stringResource(R.string.milestone_target_title_specific, it) }
        ?: stringResource(R.string.milestone_target_title_any)
    else -> milestone.text.orEmpty()
}

/**
 * Long-term goals, one Home card and no screen of its own. A tap completes or reopens, a long press
 * offers edit and delete. Completed ones hide behind "See all".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MilestonesCard(
    snapshot: MilestonesSnapshot,
    onToggle: (Milestone) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Milestone) -> Unit,
    onDelete: (Milestone) -> Unit,
) {
    var showingAll by rememberSaveable { mutableStateOf(false) }
    val hasCompleted = snapshot.milestones.any { it.isCompleted }
    val shown = if (showingAll) snapshot.milestones else snapshot.milestones.filter { !it.isCompleted }

    // "See all" and completing a milestone resize the card smoothly.
    Card(Modifier.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.milestones), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (hasCompleted) {
                TextButton(onClick = { showingAll = !showingAll }) {
                    Text(stringResource(if (showingAll) R.string.milestones_see_active else R.string.milestones_see_all))
                }
            }
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.milestone_add)) }
        }
        when {
            snapshot.milestones.isEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.milestones_empty_title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.milestones_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAdd) { Text(stringResource(R.string.milestone_add)) }
            }
            shown.isEmpty() -> Text(
                stringResource(R.string.milestones_all_done),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else -> Column(Modifier.padding(bottom = 8.dp)) {
                shown.forEach { milestone ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        MilestoneRow(
                            milestone, snapshot,
                            Modifier.combinedClickable(onClick = { onToggle(milestone) }, onLongClick = { menu = true }),
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit(milestone) })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete(milestone) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(milestone: Milestone, snapshot: MilestonesSnapshot, modifier: Modifier) {
    val done = milestone.isCompleted
    ListItem(
        modifier = modifier.alpha(if (done) 0.5f else 1f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(milestoneTitle(milestone), textDecoration = if (done) TextDecoration.LineThrough else null) },
        supportingContent = if (done) null else when (milestone.kind) {
            Milestone.ELO -> {
                {
                    val progress = milestone.eloProgress(milestone.ratingType?.let(snapshot.latestRatings::get))
                    if (progress != null) {
                        AnimatedLinearProgress(progress.toFloat(), Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp))
                    } else {
                        Text(stringResource(R.string.milestone_connect_lichess))
                    }
                }
            }
            Milestone.TITLE -> snapshot.currentLichessTitle?.let { { Text(stringResource(R.string.milestone_current_title, it)) } }
            else -> null
        },
    )
}

private val lichessTitles = listOf("GM", "WGM", "IM", "WIM", "FM", "WFM", "CM", "WCM", "NM")

/** Add or edit a milestone: pick a kind, then its one or two fields. Editing skips the kind step. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MilestoneSheet(editing: Milestone?, onDismiss: () -> Unit, onSave: (Milestone) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(editing?.kind) }
    var ratingType by rememberSaveable { mutableStateOf(editing?.ratingType ?: RatingType.RAPID) }
    var targetRating by rememberSaveable { mutableStateOf(editing?.targetRating?.toString().orEmpty()) }
    var targetTitle by rememberSaveable { mutableStateOf(editing?.targetTitle) }
    var text by rememberSaveable { mutableStateOf(editing?.text.orEmpty()) }

    fun build(): Milestone? {
        val base = editing ?: Milestone(kind = kind ?: return null)
        return when (kind) {
            Milestone.ELO -> targetRating.toIntOrNull()?.takeIf { it in 100..3500 }?.let {
                Milestone(base.id, Milestone.ELO, ratingType = ratingType, targetRating = it, completedAt = base.completedAt, createdAt = base.createdAt)
            }
            Milestone.TITLE -> Milestone(base.id, Milestone.TITLE, targetTitle = targetTitle, completedAt = base.completedAt, createdAt = base.createdAt)
            Milestone.FREE_TEXT -> text.trim().takeIf { it.isNotEmpty() }?.let {
                Milestone(base.id, Milestone.FREE_TEXT, text = it, completedAt = base.completedAt, createdAt = base.createdAt)
            }
            else -> null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.milestone_add), style = MaterialTheme.typography.titleLarge)
            when (kind) {
                null -> {
                    Text(stringResource(R.string.milestone_add_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    KindRow(Icons.Rounded.TrendingUp, stringResource(R.string.milestone_kind_elo)) { kind = Milestone.ELO }
                    KindRow(Icons.Rounded.MilitaryTech, stringResource(R.string.milestone_kind_title)) { kind = Milestone.TITLE }
                    KindRow(Icons.Rounded.Flag, stringResource(R.string.milestone_kind_text), stringResource(R.string.milestone_kind_text_body)) { kind = Milestone.FREE_TEXT }
                }
                Milestone.ELO -> {
                    Text(stringResource(R.string.milestone_rating_type), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ratingTypeOrder.forEach { type ->
                            FilterChip(selected = ratingType == type, onClick = { ratingType = type }, label = { Text(stringResource(ratingTypeNameRes(type))) })
                        }
                    }
                    OutlinedTextField(
                        value = targetRating,
                        onValueChange = { targetRating = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.milestone_target_rating)) },
                        placeholder = { Text("2000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Milestone.TITLE -> {
                    Text(stringResource(R.string.milestone_target_title), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = targetTitle == null, onClick = { targetTitle = null }, label = { Text(stringResource(R.string.milestone_any_title)) })
                        lichessTitles.forEach { title ->
                            FilterChip(selected = targetTitle == title, onClick = { targetTitle = title }, label = { Text(title) })
                        }
                    }
                }
                else -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.milestone_goal)) },
                    placeholder = { Text(stringResource(R.string.milestone_goal_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (kind != null) {
                val milestone = build()
                Button(onClick = { milestone?.let(onSave) }, enabled = milestone != null, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun KindRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String? = null, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(title) },
            supportingContent = body?.let { { Text(it) } },
        )
    }
}

@Composable
fun DeleteMilestoneDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.milestone_delete_title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
