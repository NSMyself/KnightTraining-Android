package com.knightindustries.knighttraining.feature.plan

import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.PlanSuggestion
import com.knightindustries.knighttraining.core.model.PlanSuggestionEngine
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.PlanSuggestionReason
import com.knightindustries.knighttraining.core.model.PlanSuggestionRequest
import com.knightindustries.knighttraining.core.model.RatingScale
import com.knightindustries.knighttraining.core.model.SuggestedGoal
import com.knightindustries.knighttraining.core.store.catalogCourse
import com.knightindustries.knighttraining.feature.resources.openLink
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.blockTitle
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.targetText
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.UUID

private const val DEFAULT_WEEKLY_MINUTES = 4 * 60
private val WEEKLY_MINUTES_RANGE = 60..20 * 60
private const val WEEKLY_MINUTES_STEP = 30

/**
 * "Suggest a plan" (iOS `SuggestPlanSheet`): weekly time, focus areas and a rating in; a plan sized
 * by [PlanSuggestionEngine] out, which joins the builder as ordinary, editable blocks. The engine
 * sets every number. The free-text note iOS reads with Apple Intelligence waits for an on-device
 * model here, so it's left out rather than shown and ignored.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuggestPlanSheet(
    container: AppContainer,
    history: PlanSuggestionHistory?,
    onDismiss: () -> Unit,
    onAdd: (List<DraftBlock>) -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var weeklyMinutes by rememberSaveable { mutableStateOf(DEFAULT_WEEKLY_MINUTES) }
    var focus by rememberSaveable { mutableStateOf(listOf<String>()) }
    var ratingText by rememberSaveable { mutableStateOf("") }
    var onLichess by rememberSaveable { mutableStateOf(false) }
    var rapid by rememberSaveable { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<PlanSuggestion?>(null) }
    val resourceTitles = history?.activeResources.orEmpty().associate { it.resourceId to it.title }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val result = suggestion
            if (result == null) {
                Text(stringResource(R.string.suggest_plan), style = MaterialTheme.typography.titleLarge)

                Label(stringResource(R.string.suggest_time_per_week))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalIconButton(
                        onClick = { weeklyMinutes -= WEEKLY_MINUTES_STEP },
                        enabled = weeklyMinutes > WEEKLY_MINUTES_RANGE.first,
                    ) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.suggest_less_time)) }
                    Text(durationText(weeklyMinutes * 60), style = MaterialTheme.typography.headlineSmall)
                    FilledTonalIconButton(
                        onClick = { weeklyMinutes += WEEKLY_MINUTES_STEP },
                        enabled = weeklyMinutes < WEEKLY_MINUTES_RANGE.last,
                    ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.suggest_more_time)) }
                }

                Label(stringResource(R.string.suggest_focus))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActivityType.pickableCodes.forEach { code ->
                        FilterChip(
                            selected = code in focus,
                            onClick = { focus = if (code in focus) focus - code else focus + code },
                            label = { Text(activityName(code)) },
                            leadingIcon = { Icon(activityIcon(code), contentDescription = null) },
                        )
                    }
                }

                // A connected Lichess account already gives the rating.
                if (history != null && history?.lichessRating == null) {
                    Label(stringResource(R.string.suggest_rating))
                    OutlinedTextField(
                        value = ratingText,
                        onValueChange = { ratingText = it.filter(Char::isDigit).take(4) },
                        placeholder = { Text(stringResource(R.string.suggest_rating_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !onLichess, onClick = { onLichess = false }, label = { Text(stringResource(R.string.suggest_site_chesscom)) })
                        FilterChip(selected = onLichess, onClick = { onLichess = true }, label = { Text(stringResource(R.string.lichess)) })
                        FilterChip(selected = !rapid, onClick = { rapid = false }, label = { Text(stringResource(R.string.rating_blitz)) })
                        FilterChip(selected = rapid, onClick = { rapid = true }, label = { Text(stringResource(R.string.rating_rapid)) })
                    }
                }

                Button(
                    onClick = {
                        val scale = when {
                            onLichess && rapid -> RatingScale.LICHESS_RAPID
                            onLichess -> RatingScale.LICHESS_BLITZ
                            rapid -> RatingScale.CHESS_COM_RAPID
                            else -> RatingScale.CHESS_COM_BLITZ
                        }
                        val request = PlanSuggestionRequest(weeklyMinutes, ratingText.toIntOrNull(), scale, focus)
                        suggestion = PlanSuggestionEngine.suggest(request, history ?: PlanSuggestionHistory())
                    },
                    enabled = history != null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) { Text(stringResource(R.string.suggest_go)) }
            } else {
                Text(stringResource(R.string.suggest_result_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.suggest_budget, durationText(result.budgetMinutes * 60)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    result.goals.forEach { goal ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(activityIcon(goal.activityTypeCode), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text(blockTitle(null, goal.activityTypeCode, goal.timeControlLabel)) },
                            supportingContent = {
                                Text(listOfNotNull(targetText(goal.activityTypeCode, goal.targetType, goal.targetValue), goal.resourceId?.let(resourceTitles::get)).joinToString(" · "))
                            },
                        )
                    }
                }
                result.reasons.forEach { reason ->
                    Text("• " + reasonText(reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                result.course?.let { course ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.suggest_partner), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(course.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.suggest_course_block, activityName(course.activityTypeCode)), style = MaterialTheme.typography.bodyMedium)
                            course.weeksAtThisPace?.let {
                                Text(pluralStringResource(R.plurals.suggest_course_weeks, it, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                scope.launch { container.store.catalogCourse(course.catalogCourseId)?.referralUrl?.let { openLink(context, it) } }
                            }) { Text(stringResource(R.string.suggest_view_course)) }
                        }
                    }
                }
                Button(
                    onClick = { onAdd(result.goals.map { it.toDraft(resourceTitles) }) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.suggest_add)) }
                TextButton(onClick = { suggestion = null }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(stringResource(R.string.suggest_change))
                }
            }
        }
    }
}

private fun SuggestedGoal.toDraft(titles: Map<UUID, String>) = DraftBlock(
    name = null, activityTypeCode = activityTypeCode, targetType = targetType, targetValue = targetValue,
    timeControlLabel = timeControlLabel, resourceId = resourceId, resourceTitle = resourceId?.let(titles::get),
)

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun reasonText(reason: PlanSuggestionReason): String {
    val percent = NumberFormat.getPercentInstance()
    return when (reason) {
        is PlanSuggestionReason.BudgetCalibrated -> stringResource(
            R.string.suggest_reason_calibrated,
            durationText(reason.requestedMinutes * 60), durationText(reason.averageMinutes * 60),
            percent.format(reason.completionRate), durationText(reason.plannedMinutes * 60),
        )
        is PlanSuggestionReason.Rating -> stringResource(if (reason.fromLichess) R.string.suggest_reason_lichess_rating else R.string.suggest_reason_rating, reason.rating)
        PlanSuggestionReason.NoRating -> stringResource(R.string.suggest_reason_no_rating)
        is PlanSuggestionReason.Focus -> stringResource(R.string.suggest_reason_focus, ListFormatter.getInstance().format(reason.codes.map { activityName(it) }))
        is PlanSuggestionReason.LighterOn -> stringResource(R.string.suggest_reason_lighter, activityName(reason.code), percent.format(reason.completionRate))
        is PlanSuggestionReason.CoachTargetApplied -> stringResource(R.string.suggest_reason_coach_target, targetText(reason.code, reason.targetType, reason.value))
        is PlanSuggestionReason.LinkedResource -> stringResource(R.string.suggest_reason_resource, activityName(reason.activityTypeCode), reason.title)
    }
}
