package com.knightindustries.knighttraining.feature.plan

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.PlanEstimator
import com.knightindustries.knighttraining.core.model.PlanEstimatorBlock
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.store.allPlanTemplates
import com.knightindustries.knighttraining.core.store.createWeeklyPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The weekly commitment (iOS `PlanThisWeekScreen`): plans are never auto-renewed, the user picks
 * one each week. Defaults to the plan used last.
 */
class PlanThisWeekViewModel(private val container: AppContainer) : ViewModel() {
    private val _templates = MutableStateFlow<List<PlanTemplate>?>(null)
    val templates: StateFlow<List<PlanTemplate>?> = _templates
    var selectedId by mutableStateOf<UUID?>(null)
    var isCommitting by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val all = container.store.allPlanTemplates()
            _templates.value = all
            selectedId = (all.firstOrNull { it.isActive } ?: all.firstOrNull())?.id
        }
    }

    fun commit(onCommitted: () -> Unit) {
        val id = selectedId ?: return
        isCommitting = true
        viewModelScope.launch {
            try {
                container.store.createWeeklyPlan(id, container.preferences.firstLaunch())
                onCommitted()
            } finally {
                isCommitting = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanThisWeekScreen(viewModel: PlanThisWeekViewModel, onBack: () -> Unit, onCreatePlan: () -> Unit, onCommitted: () -> Unit) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_this_week)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Button(
                enabled = viewModel.selectedId != null && !viewModel.isCommitting,
                onClick = { viewModel.commit(onCommitted) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) { Text(stringResource(R.string.commit_plan)) }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.plan_this_week_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(templates.orEmpty(), key = { it.id }) { template ->
                val selected = template.id == viewModel.selectedId
                Card(
                    onClick = { viewModel.selectedId = template.id },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(template.displayName(stringResource(R.string.my_training_plan)), style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(R.plurals.goal_count, template.goalCount, template.goalCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PlanEstimator.estimate(template.goalTemplates.map { PlanEstimatorBlock(it.activityType.code, it.targetType, it.targetValue, it.timeControlLabel) })
                                ?.let { EstimateText(it) }
                        }
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }
            item {
                OutlinedButton(onClick = onCreatePlan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.create_new_plan), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
