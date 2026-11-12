package com.knightindustries.knighttraining.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.store.GoalEdit
import com.knightindustries.knighttraining.core.store.archivePlanTemplate
import com.knightindustries.knighttraining.core.store.nextUntitledPlanTemplateName
import com.knightindustries.knighttraining.core.store.planTemplate
import com.knightindustries.knighttraining.core.store.qualifyingSessionCount
import com.knightindustries.knighttraining.core.store.renamePlanTemplate
import com.knightindustries.knighttraining.core.store.resource
import com.knightindustries.knighttraining.core.store.updatePlanBlocks
import com.knightindustries.knighttraining.core.store.weeklyPlanContaining
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.blockTitle
import com.knightindustries.knighttraining.ui.targetText
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/** Whether edits reach this week's goals too (iOS AGENTS.md §7's "editable until 2 sessions"). */
enum class WeekLock { NOT_THIS_WEEK, UNLOCKED, LAST_CHANCE, LOCKED }

/**
 * Edits a saved plan (iOS `EditTrainingPlanViewModel`). Changes always reach the template; they
 * reach the committed week too while it's this template's and fewer than two sessions have been
 * trained against it. Kept blocks keep their id, so the week's progress on them survives.
 */
class EditPlanViewModel(private val container: AppContainer, private val templateId: UUID) : ViewModel() {
    var name by mutableStateOf("")
    val blocks = mutableStateListOf<DraftBlock>()
    private val existingIds = mutableSetOf<UUID>()
    var lock by mutableStateOf(WeekLock.NOT_THIS_WEEK)
        private set
    private var weeklyPlanId: UUID? = null
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val template = container.store.planTemplate(templateId) ?: return@launch
            name = template.name.orEmpty()
            template.goalTemplates.forEach {
                existingIds += it.id
                blocks += DraftBlock(it.id, it.name, it.activityType.code, it.targetType, it.targetValue, it.timeControlLabel, it.resource?.id, it.resource?.title)
            }
            val week = container.store.weeklyPlanContaining(Instant.now(), container.preferences.firstLaunch())
            if (week?.planTemplateId == templateId) {
                weeklyPlanId = week.id
                val sessions = container.store.qualifyingSessionCount(week.id)
                lock = when {
                    sessions >= WeeklyPlan.EDITABLE_SESSION_LIMIT -> WeekLock.LOCKED
                    sessions == WeeklyPlan.EDITABLE_SESSION_LIMIT - 1 -> WeekLock.LAST_CHANCE
                    else -> WeekLock.UNLOCKED
                }
            }
            loaded = true
        }
    }

    fun save(untitledBase: String, untitledNumbered: (Int) -> String, onSaved: () -> Unit) {
        viewModelScope.launch {
            val store = container.store
            val resolved = name.trim().ifEmpty { store.nextUntitledPlanTemplateName(untitledBase, untitledNumbered, excludingId = templateId) }
            store.renamePlanTemplate(templateId, resolved)
            store.updatePlanBlocks(
                planTemplateId = templateId,
                weeklyPlanId = weeklyPlanId.takeIf { lock == WeekLock.UNLOCKED || lock == WeekLock.LAST_CHANCE },
                blocks = blocks.map { block ->
                    GoalEdit(
                        id = block.id.takeIf { it in existingIds }, name = block.name,
                        activityType = store.activityType(block.activityTypeCode)!!, resource = block.resourceId?.let { store.resource(it) },
                        targetType = block.targetType, targetValue = block.targetValue, timeControlLabel = block.timeControlLabel,
                    )
                },
            )
            onSaved()
        }
    }

    /** An archive, never a delete: weeks trained with it keep their history. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            container.store.archivePlanTemplate(templateId)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanScreen(viewModel: EditPlanViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<DraftBlock?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_plan)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel)) } },
                actions = {
                    TextButton(
                        enabled = viewModel.loaded && viewModel.blocks.isNotEmpty(),
                        onClick = {
                            viewModel.save(context.getString(R.string.untitled_plan), { context.getString(R.string.untitled_plan_numbered, it) }, onClose)
                        },
                    ) { Text(stringResource(R.string.save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item {
                OutlinedTextField(
                    value = viewModel.name, onValueChange = { viewModel.name = it },
                    label = { Text(stringResource(R.string.plan_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val note = when (viewModel.lock) {
                    WeekLock.NOT_THIS_WEEK -> R.string.edit_plan_future
                    WeekLock.UNLOCKED -> R.string.edit_plan_unlocked
                    WeekLock.LAST_CHANCE -> R.string.edit_plan_last_chance
                    WeekLock.LOCKED -> R.string.edit_plan_locked
                }
                Text(stringResource(note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(viewModel.blocks, key = { it.id }) { block ->
                Card(onClick = { editing = block }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(activityIcon(block.activityTypeCode), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(blockTitle(block.name, block.activityTypeCode, block.timeControlLabel)) },
                        supportingContent = { Text(listOfNotNull(targetText(block.activityTypeCode, block.targetType, block.targetValue), block.resourceTitle).joinToString(" · ")) },
                    )
                }
            }
            item {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.add_weekly_goal), modifier = Modifier.padding(start = 8.dp))
                }
            }
            // Never for this week's plan (iOS): deleting it mid-week has no sensible meaning.
            if (viewModel.loaded && viewModel.lock == WeekLock.NOT_THIS_WEEK) {
                item {
                    TextButton(
                        onClick = { confirmingDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.delete_plan)) }
                }
            }
        }
    }

    if (adding) BlockSheet(initial = null, onDismiss = { adding = false }, onSave = { viewModel.blocks += it; adding = false })
    editing?.let { block ->
        BlockSheet(
            initial = block,
            onDismiss = { editing = null },
            onSave = { updated -> viewModel.blocks.replaceAll { if (it.id == updated.id) updated else it }; editing = null },
            onRemove = { viewModel.blocks.removeAll { it.id == block.id }; editing = null },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_plan_title)) },
            text = { Text(stringResource(R.string.delete_plan_body)) },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; viewModel.delete(onClose) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
