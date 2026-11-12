package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.persistence.entities.PlanTemplateEntity
import com.knightindustries.knighttraining.core.persistence.toDomain
import com.knightindustries.knighttraining.core.persistence.toEntity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

private suspend fun TrainingStore.withGoalTemplates(templates: List<PlanTemplateEntity>): List<PlanTemplate> {
    if (templates.isEmpty()) return emptyList()
    val goals = planDao.goalTemplates(templates.map { it.id })
    val resources = resourcesById(goals.map { it.resourceId })
    val byPlan = goals.groupBy({ it.planTemplateId }, { it.toDomain(resources) })
    return templates.map { it.toDomain(byPlan[it.id].orEmpty()) }
}

/** Saves the template row only; blocks go through [saveGoalTemplate] or [updatePlanBlocks]. */
suspend fun TrainingStore.savePlanTemplate(template: PlanTemplate) = planDao.upsertTemplate(template.toEntity())

suspend fun TrainingStore.saveGoalTemplate(goal: GoalTemplate) = planDao.upsertGoalTemplate(goal.toEntity())

/** `null` for an archived (deleted) template, so a stale reference can never commit it. */
suspend fun TrainingStore.planTemplate(id: UUID): PlanTemplate? =
    read { planDao.template(id)?.let { withGoalTemplates(listOf(it)).single() } }

suspend fun TrainingStore.activePlanTemplate(): PlanTemplate? =
    read { planDao.activeTemplate()?.let { withGoalTemplates(listOf(it)).single() } }

/** Every template the user can still see, most recently updated first. */
suspend fun TrainingStore.allPlanTemplates(): List<PlanTemplate> = read { withGoalTemplates(planDao.allTemplates()) }

suspend fun TrainingStore.goalTemplate(id: UUID): GoalTemplate? = read {
    planDao.goalTemplate(id)?.let { goal -> goal.toDomain(resourcesById(listOf(goal.resourceId))) }
}

suspend fun TrainingStore.goalTemplates(planTemplateId: UUID): List<GoalTemplate> = read {
    val goals = planDao.goalTemplates(listOf(planTemplateId))
    val resources = resourcesById(goals.map { it.resourceId })
    goals.map { it.toDomain(resources) }
}

suspend fun TrainingStore.deleteGoalTemplate(id: UUID) = planDao.deleteGoalTemplate(id)

suspend fun TrainingStore.setActivePlanTemplate(id: UUID) {
    val at = now()
    write {
        planDao.deactivateAll(at)
        planDao.activate(id, at)
    }
    emit(ActivePlanTemplateChanged(id))
}

/**
 * "Delete Plan": flags the template instead of deleting it, so every week already committed from
 * it (including this one) keeps running and displaying. Also clears `isActive`.
 */
suspend fun TrainingStore.archivePlanTemplate(id: UUID) = planDao.archive(id, now())

/** Destroys every week committed from the template. No UI path; use [archivePlanTemplate]. */
suspend fun TrainingStore.deletePlanTemplate(id: UUID) = planDao.deleteTemplate(id)

/** A targeted update, so a caller holding a partial view can't clobber `isActive`/`isArchived`. */
suspend fun TrainingStore.renamePlanTemplate(id: UUID, name: String) = planDao.rename(id, name, now())

/**
 * The name to save an unnamed plan under (see [PlanTemplate.untitledName]). Only visible plans
 * count, and [excludingId] (a plan being renamed) doesn't block its own name.
 */
suspend fun TrainingStore.nextUntitledPlanTemplateName(
    base: String,
    numbered: (Int) -> String,
    excludingId: UUID? = null,
): String = PlanTemplate.untitledName(planDao.visibleTemplateNames(excludingId), base, numbered)

/** Live list of the plans the user can see, for the Training tab, which stays mounted. */
fun TrainingStore.observePlanTemplates(): kotlinx.coroutines.flow.Flow<List<PlanTemplate>> =
    db.invalidationTracker.createFlow("plan_template", "goal_template", "resource")
        .map { allPlanTemplates() }
        .distinctUntilChanged()
