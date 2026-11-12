package com.knightindustries.knighttraining.feature.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.PlanEstimate
import com.knightindustries.knighttraining.core.model.PlanEstimator
import com.knightindustries.knighttraining.core.model.PlanEstimatorBlock
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.store.NewBlock
import com.knightindustries.knighttraining.core.store.createPlan
import com.knightindustries.knighttraining.core.store.planSuggestionHistory
import com.knightindustries.knighttraining.core.store.resource
import java.util.UUID
import kotlinx.coroutines.launch

/** A training block being built, before anything is saved. */
data class DraftBlock(
    val id: UUID = UUID.randomUUID(),
    /** `null` derives the title from the activity (see `blockTitle`). */
    val name: String?,
    val activityTypeCode: String,
    val targetType: TargetType,
    val targetValue: Int,
    val timeControlLabel: String? = null,
    /** The linked resource, if any: carried to the goal template, its weekly goals and sessions. */
    val resourceId: UUID? = null,
    /** For display only. */
    val resourceTitle: String? = null,
)

/**
 * The plan builder (iOS `CreateTrainingPlanViewModel`): a custom plan of one or more blocks, with
 * presets imported as blocks rather than being a separate plan type. Saving needs a block, not a
 * name.
 */
class CreatePlanViewModel(
    private val container: AppContainer,
    private val commitsToCurrentWeek: Boolean,
) : ViewModel() {
    var name by mutableStateOf("")
    val blocks = mutableStateListOf<DraftBlock>()
    var isSaving by mutableStateOf(false)
        private set
    var hasImportedTemplate by mutableStateOf(false)
        private set

    val canSave: Boolean get() = blocks.isNotEmpty() && !isSaving

    val hasUnsavedChanges: Boolean get() = name.isNotBlank() || blocks.isNotEmpty()

    val estimate: PlanEstimate?
        get() = PlanEstimator.estimate(blocks.map { PlanEstimatorBlock(it.activityTypeCode, it.targetType, it.targetValue, it.timeControlLabel) })

    /**
     * A block matching an existing one on everything but the target merges into it, summing the
     * targets, instead of adding a second card nobody could tell apart.
     */
    fun addBlock(block: DraftBlock) {
        val index = blocks.indexOfFirst {
            it.activityTypeCode == block.activityTypeCode && it.targetType == block.targetType &&
                it.name == block.name && it.timeControlLabel == block.timeControlLabel && it.resourceId == block.resourceId
        }
        if (index >= 0) {
            blocks[index] = blocks[index].copy(targetValue = blocks[index].targetValue + block.targetValue)
        } else {
            blocks += block
        }
    }

    fun updateBlock(block: DraftBlock) {
        val index = blocks.indexOfFirst { it.id == block.id }
        if (index >= 0) blocks[index] = block
    }

    fun removeBlock(id: UUID) {
        blocks.removeAll { it.id == id }
    }

    /** Adds every item of [presets] through [addBlock], so repeats merge rather than duplicate. */
    fun importPresets(presets: List<PlanPreset>, title: (Int) -> String) {
        hasImportedTemplate = true
        for (preset in presets) {
            for (item in preset.items) {
                addBlock(DraftBlock(name = title(item.title), activityTypeCode = item.activityTypeCode, targetType = item.targetType, targetValue = item.targetValue, timeControlLabel = item.timeControlLabel))
            }
        }
    }

    /** What "Suggest a plan" knows about the player; loaded when the sheet opens. */
    var suggestionHistory by mutableStateOf<PlanSuggestionHistory?>(null)
        private set

    fun loadSuggestionHistory() {
        viewModelScope.launch { suggestionHistory = container.store.planSuggestionHistory() }
    }

    /** A suggested plan's blocks, through [addBlock] like a preset's. */
    fun importSuggestion(drafts: List<DraftBlock>) {
        hasImportedTemplate = true
        drafts.forEach(::addBlock)
    }

    fun save(untitledBase: String, untitledNumbered: (Int) -> String, onSaved: () -> Unit) {
        if (!canSave) return
        isSaving = true
        viewModelScope.launch {
            try {
                container.store.createPlan(
                    name = name,
                    blocks = blocks.map {
                        NewBlock(
                            it.name, container.store.activityType(it.activityTypeCode)!!, it.targetType, it.targetValue,
                            it.timeControlLabel, it.resourceId?.let { id -> container.store.resource(id) },
                        )
                    },
                    untitledBase = untitledBase,
                    untitledNumbered = untitledNumbered,
                    commit = commitsToCurrentWeek,
                    firstLaunch = container.preferences.firstLaunch(),
                )
                onSaved()
            } finally {
                isSaving = false
            }
        }
    }
}
