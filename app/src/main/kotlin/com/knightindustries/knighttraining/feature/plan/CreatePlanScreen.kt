package com.knightindustries.knighttraining.feature.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.PlanEstimate
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.blockTitle
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.targetText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(viewModel: CreatePlanViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<DraftBlock?>(null) }
    var addingBlock by rememberSaveable { mutableStateOf(false) }
    var choosingPreset by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }

    fun close() = if (viewModel.hasUnsavedChanges) confirmingDiscard = true else onClose()
    BackHandler(enabled = viewModel.hasUnsavedChanges) { confirmingDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_plan_title)) },
                navigationIcon = {
                    IconButton(onClick = ::close) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel)) }
                },
                actions = {
                    TextButton(
                        enabled = viewModel.canSave,
                        onClick = {
                            viewModel.save(
                                untitledBase = context.getString(R.string.untitled_plan),
                                untitledNumbered = { context.getString(R.string.untitled_plan_numbered, it) },
                                onSaved = onSaved,
                            )
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
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text(stringResource(R.string.plan_name_label)) },
                    placeholder = { Text(stringResource(R.string.untitled_plan)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.plan_goals_header), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    viewModel.estimate?.let { EstimateText(it) }
                }
            }
            items(viewModel.blocks, key = { it.id }) { block ->
                Card(
                    onClick = { editing = block },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(activityIcon(block.activityTypeCode), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(blockTitle(block.name, block.activityTypeCode, block.timeControlLabel)) },
                        supportingContent = { Text(listOfNotNull(targetText(block.activityTypeCode, block.targetType, block.targetValue), block.resourceTitle).joinToString(" · ")) },
                    )
                }
            }
            item {
                OutlinedButton(onClick = { addingBlock = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.add_weekly_goal), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (!viewModel.hasImportedTemplate) {
                item {
                    TextButton(onClick = { choosingPreset = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Text(stringResource(R.string.import_template), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (addingBlock) {
        BlockSheet(initial = null, onDismiss = { addingBlock = false }, onSave = { viewModel.addBlock(it); addingBlock = false })
    }
    editing?.let { block ->
        BlockSheet(
            initial = block,
            onDismiss = { editing = null },
            onSave = { viewModel.updateBlock(it); editing = null },
            onRemove = { viewModel.removeBlock(block.id); editing = null },
        )
    }
    if (choosingPreset) {
        PresetSheet(
            onDismiss = { choosingPreset = false },
            onImport = { presets ->
                viewModel.importPresets(presets) { context.getString(it) }
                choosingPreset = false
            },
        )
    }
    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.discard_plan_title)) },
            text = { Text(stringResource(R.string.discard_plan_body)) },
            confirmButton = { TextButton(onClick = { confirmingDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
}

@Composable
fun EstimateText(estimate: PlanEstimate) {
    val time = durationText(estimate.minutes * 60)
    Text(
        stringResource(if (estimate.isLowerBound) R.string.estimated_time_floor else R.string.estimated_time, time),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Presets, several at once; their items join the plan as ordinary blocks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSheet(onDismiss: () -> Unit, onImport: (List<PlanPreset>) -> Unit) {
    val selected = remember { mutableStateListOf<Int>() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.choose_template), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                stringResource(R.string.template_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(PlanPreset.all.indices.toList()) { index ->
                    val preset = PlanPreset.all[index]
                    val isSelected = index in selected
                    Card(
                        onClick = { if (isSelected) selected.remove(index) else selected.add(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(preset.title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(preset.description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                preset.items.forEach { item ->
                                    Text(
                                        "• " + stringResource(item.title) + " · " + targetText(item.activityTypeCode, item.targetType, item.targetValue),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                preset.estimate?.let { EstimateText(it) }
                            }
                            Checkbox(checked = isSelected, onCheckedChange = null)
                        }
                    }
                }
            }
            Button(
                enabled = selected.isNotEmpty(),
                onClick = { onImport(selected.sorted().map { PlanPreset.all[it] }) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text(stringResource(R.string.import_action)) }
        }
    }
}
