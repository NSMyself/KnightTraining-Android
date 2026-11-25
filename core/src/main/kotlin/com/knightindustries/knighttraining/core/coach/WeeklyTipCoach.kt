package com.knightindustries.knighttraining.core.coach

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * An on-device language model, reduced to what the coach needs: instructions plus a prompt in,
 * text out. The app backs it with Gemini Nano; tests with a script.
 */
fun interface LanguageModel {
    suspend fun respond(instructions: String, prompt: String): String
}

@Serializable
data class WeeklyTipSuggestion(
    val tip: String,
    val activityTypeCode: String? = null,
    val suggestedTargetValue: Int? = null,
    val focus: String? = null,
)

/** `reason` comes first so the model argues before it scores (iOS saw verdicts contradict their reasons the other way round). */
@Serializable
data class WeeklyTipVerdict(
    val reason: String = "",
    val isChessRelated: Boolean = false,
    val isSound: Boolean = false,
    val isPolite: Boolean = false,
    val recommendsBannedTimeControl: Boolean = false,
    val justRepeatsWishIDone: Boolean = false,
    val focusIsGrounded: Boolean = false,
    val isSpecific: Boolean = false,
    val isActionable: Boolean = false,
)

/**
 * The weekly coach (iOS `WeeklyTipCoach`): one structured suggestion from the model, deterministic
 * backstops, then an independent second pass as a skeptical reviewer. A tip that fails either
 * never reaches the player. The prompts are iOS's, word for word; JSON in the prompt stands in for
 * Apple's guided generation.
 */
class WeeklyTipCoach(private val model: LanguageModel) {
    data class Input(
        val plan: WeeklyPlan,
        val review: WeeklyReview? = null,
        val notedActivities: List<Activity> = emptyList(),
        /** A broader pool than the week, summarized into the two most recent sittings. */
        val recentActivities: List<Activity> = emptyList(),
        val puzzleStatus: LichessPuzzleStatus? = null,
        val lastWeekFocus: String? = null,
        val coachingGate: CoachingSuggestion.Gate = CoachingSuggestion.Gate.CLOSED,
    )

    sealed interface Outcome {
        val prompt: String
        val suggestion: WeeklyTipSuggestion

        data class Approved(
            override val prompt: String,
            override val suggestion: WeeklyTipSuggestion,
            val verdict: WeeklyTipVerdict,
            val coachDuration: Duration,
            val verifyDuration: Duration,
        ) : Outcome

        /** `verdict` is `null` when a deterministic backstop caught it before review. */
        data class Rejected(
            override val prompt: String,
            override val suggestion: WeeklyTipSuggestion,
            val verdict: WeeklyTipVerdict?,
            val reason: String,
        ) : Outcome
    }

    /** The model's reply wasn't the JSON asked for. */
    class UnreadableResponse(val raw: String) : Exception("The model's reply wasn't the JSON asked for")

    /**
     * @param languageName the app's language in English ("Portuguese (Portugal)"), or `null` for
     *   English: the tip and focus are written in it, everything else stays as is.
     */
    suspend fun run(input: Input, languageName: String?, zone: ZoneId = ZoneId.systemDefault()): Outcome {
        val notes = noteLines(input.review, input.notedActivities)
        val context = buildContext(
            input.plan, notes, recentSessionLines(input.recentActivities, zone = zone), input.puzzleStatus,
            loggedPuzzleCount(input.recentActivities), input.lastWeekFocus, lastLessonLine(input.recentActivities, zone),
            input.coachingGate.suggestion,
        )

        val clock = TimeSource.Monotonic
        val coachStart = clock.markNow()
        val tip = ask(coachInstructions(languageName), context, SUGGESTION_SCHEMA, WeeklyTipSuggestion.serializer())
        val coachDuration = coachStart.elapsedNow()

        fun rejected(reason: String) = Outcome.Rejected(context, tip, null, reason)
        bannedTimeControl(tip.tip)?.let {
            return rejected("Recommended $it, which this app doesn't consider suitable for serious improvement — rejected before review.")
        }
        if (!input.coachingGate.mayMentionCoach && CoachingSuggestion.mentionsCoach(tip.tip, languageName)) {
            return rejected("Suggested a coach in a week the coaching gate was closed — rejected before review.")
        }
        if (echoesWishIDone(tip.tip, input.review?.wishIDone)) {
            return rejected("Just restated what the player already said they wished they'd done differently, instead of building on it — rejected before review.")
        }
        if (CoachTargetChange.isMisread(tip.activityTypeCode, tip.suggestedTargetValue, input.plan)) {
            return rejected("Gave one goal another goal's target number, far outside half to double its own, so the tip around it is suspect too — rejected before review.")
        }

        val verifyStart = clock.markNow()
        val verdict = verify(context, tip, languageName)
        val verifyDuration = verifyStart.elapsedNow()

        val repeatsWish = reallyRepeatsWishIDone(input.review?.wishIDone, verdict.justRepeatsWishIDone)
        val passes = verdict.isChessRelated && verdict.isSound && verdict.isPolite && verdict.isSpecific && verdict.isActionable &&
            !verdict.recommendsBannedTimeControl && !repeatsWish
        if (!passes) return Outcome.Rejected(context, tip, verdict, verdict.reason.ifBlank { "Didn't pass review." })
        return Outcome.Approved(context, tip, verdict, coachDuration, verifyDuration)
    }

    suspend fun verify(context: String, tip: WeeklyTipSuggestion, languageName: String?): WeeklyTipVerdict =
        ask(verifierInstructions(languageName), verificationPrompt(context, tip), VERDICT_SCHEMA, WeeklyTipVerdict.serializer())

    private suspend fun <T> ask(instructions: String, prompt: String, schema: String, serializer: KSerializer<T>): T {
        val raw = model.respond(instructions, "$prompt\n\n$schema")
        return parse(raw, serializer) ?: throw UnreadableResponse(raw)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** The JSON object in a reply, even wrapped in prose or a code fence; `null` if there isn't one. */
        fun <T> parse(raw: String, serializer: KSerializer<T>): T? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching { json.decodeFromString(serializer, raw.substring(start, end + 1)) }.getOrNull()
        }

        private val SUGGESTION_SCHEMA = unwrap(
            """
            Respond with only a JSON object, no other text, with these fields:
            "tip" (string): One or two short, warm, specific sentences suggesting an adjustment for the coming week's plan, referencing what actually happened.
            "activityTypeCode" (string or null): The activity type code this tip is about, if it targets one specific goal — one of: puzzles, playing, analyzing, openings, endgames. Null if the tip is general advice (e.g. about timing or habits) not tied to a single goal.
            "suggestedTargetValue" (integer or null): A specific new target value to suggest for that activity's goal, only if the tip proposes a concrete number change. A number of puzzles or games for those goals; minutes for every other goal. Always in the units that goal's own plan line uses, never a number taken from a different goal.
            "focus" (string or null): Optional: one short phrase (under 8 words) naming the single chess skill to focus on in the coming week, only if a weak Lichess puzzle theme or the player's own notes about their chess support it. Null otherwise.
            """,
            keepLines = true,
        )

        private val VERDICT_SCHEMA = unwrap(
            """
            Respond with only a JSON object, no other text, with these fields in this order:
            "reason" (string): Reasoning about the tip against the week's actual data. First quote the tip's claims and the data lines behind each; then: is it chess-related, sound, polite, clear enough to act on, does the tip itself (not the player's notes) push blitz or bullet games, does it just restate something the player already said wanting to do differently? Write this out before scoring the criteria below; they should follow from this reasoning, not the other way around.
            "isChessRelated" (boolean): True if the tip is genuinely about chess training and not off-topic.
            "isSound" (boolean): True if the tip is a sound, sensible suggestion given what the player actually did last week — not exaggerated, contradictory, or disconnected from their actual performance.
            "isPolite" (boolean): True if the tone is polite and friendly — not condescending, harsh, robotic, preachy, sarcastic, or backhanded.
            "recommendsBannedTimeControl" (boolean): True if the tip recommends or encourages playing blitz or bullet games.
            "justRepeatsWishIDone" (boolean): True if the tip just restates what the player said they wished they'd done differently, rather than building on it.
            "focusIsGrounded" (boolean): True if no focus was proposed, or if the proposed focus is backed by a weak Lichess puzzle theme or something the player wrote about their own chess. False if it's invented or generic.
            "isSpecific" (boolean): True if the tip refers to something specific in this player's data (a goal, a number, a puzzle theme, a session, something they wrote). False if it's generic advice that would fit any player.
            "isActionable" (boolean): True if someone reading only the tip could picture exactly what to do. False if it's confusing, garbled, mixes separate facts into one instruction, or says something like "your weakest theme" without naming it.
            """,
            keepLines = true,
        )

        /**
         * Rejoins hard-wrapped prose: a line continues the previous one unless either is blank or
         * it starts a numbered item. With [keepLines], every line stays its own.
         */
        internal fun unwrap(text: String, keepLines: Boolean = false): String {
            val lines = text.trimIndent().lines()
            if (keepLines) return lines.joinToString("\n")
            val out = StringBuilder()
            for ((index, line) in lines.withIndex()) {
                val previous = lines.getOrNull(index - 1)
                val startsItem = Regex("^\\d+\\. ").containsMatchIn(line)
                when {
                    index == 0 -> out.append(line)
                    line.isBlank() || previous.isNullOrBlank() || startsItem -> out.append('\n').append(line)
                    else -> out.append(' ').append(line.trimStart())
                }
            }
            return out.toString()
        }

        fun coachInstructions(languageName: String?): String {
            var instructions = unwrap(
                """
                Hard rule, above everything else in these instructions: never write the words "blitz" or
                "bullet," and never suggest playing a blitz or bullet game, under any circumstance. Fast
                time controls don't leave room for the calculation and reflection committed, serious
                improvement is built on. If a "playing" goal needs a nudge, point it at rapid or classical
                games, or at analysis, instead — never at speed chess of any kind, no matter how naturally
                it seems to fit.

                You are the in-app coach for KnightTraining, a chess training habit app for busy adult
                players roughly 1000–2000 rated who train around a full-time job. A weekly plan is a
                minimum commitment, not a ceiling: hitting the target is the goal, exceeding it is a
                bonus, and falling short isn't a failure worth dwelling on.

                You're reviewing one specific week: what the player committed to, what they actually did,
                and any notes they left themselves — specifically what held them back and what they wish
                they'd done differently. From that, suggest exactly ONE small, specific, actionable
                adjustment for the coming week's plan — grounded in what actually happened, never generic
                encouragement like "keep up the great work," and never a sarcastic or backhanded reframe
                of something the player said bothered them. Build on what the player wished they'd done
                differently rather than restating it — a tip that just repeats their own words back adds
                nothing. Prefer nudging an existing goal's target number up or down over proposing a new
                activity entirely. If a note mentions something practical — timing, energy, motivation,
                avoidance — you may suggest a habit change instead of a number change. Keep the tip to
                1-2 sentences. Be warm, but direct and specific.

                The tip must be something the player can picture doing, in plain words. Each line of data
                is its own fact: don't merge two of them into one instruction (a first-try percentage is
                not a theme, and a number from one goal never belongs to another). If the tip uses the
                weakest Lichess puzzle theme, name the theme in the tip itself and say what to do with
                it, e.g. "Spend part of your puzzle time on Lichess's Pin theme." Never write "your
                weakest theme" without naming it.

                If every goal was completed and they left no notes, don't just congratulate them: look at
                their Lichess puzzles and recent sessions for something specific (a weak theme, an
                activity they keep skipping, a small stretch on a target they hit easily). If Lichess
                shows no puzzles at all in the last 30 days and they logged no puzzles in the app either,
                suggesting some puzzle practice is a good tip.

                Separately, you may name one "focus" for the coming week: a short phrase (under 8 words) naming
                the single chess skill most worth working on, e.g. "Pins" or "Endgame technique". Prefer the weakest Lichess puzzle theme when one is listed; otherwise
                use something the player wrote about their own chess (not about their schedule). If
                neither supports one, leave "focus" empty rather than inventing something generic. If they
                already chose a focus for this week, you may keep it if it still fits.

                If their latest coaching lesson's topics are listed, a tip that reinforces what the lesson
                covered (some practice on that topic this week) is a good choice.

                Never suggest getting a coach, a teacher or lessons unless the data includes a "Working
                with a coach" section, and then only as that section allows. If their plan or sessions
                already include coaching, you may mention their lessons like any other activity.
                """,
            )
            if (languageName != null) {
                instructions += "\n\n" + unwrap(
                    """
                    Write the "tip" and "focus" fields entirely in $languageName — every word of them,
                    not just part. Every other field (the activity type code, the suggested numeric target)
                    stays in its normal form regardless of language; only "tip" and "focus" are
                    natural-language content.
                    """,
                )
            }
            return instructions
        }

        fun verifierInstructions(languageName: String?): String {
            var instructions = unwrap(
                """
                You are an independent reviewer for KnightTraining, a chess training habit app for busy
                adult players roughly 1000–2000 rated. A weekly plan is a minimum commitment, not a
                ceiling — a tip recommending the player keep their target the same, or ease off after a
                rough week, is entirely sound and should not be rejected just for not pushing harder.

                You did not write the tip below — judge it independently against the week's actual data,
                on eight criteria:
                1. Is it genuinely about chess training, not off-topic?
                2. Is it a sound suggestion given what the player actually did — consistent with the data
                above, not exaggerated, invented, or contradictory? A tip suggesting a coach, a teacher
                or lessons is never sound unless the data has a "Working with a coach" section (follow
                it: the tip must tie the suggestion to the reason given there) or their plan or
                sessions already include coaching — however reasonable it otherwise sounds.
                3. Is the tone polite and friendly — not condescending, harsh, robotic, preachy,
                sarcastic, or backhanded (e.g. mocking something the player said bothered them)?
                4. Does the tip itself recommend or encourage playing blitz or bullet games? This app
                considers fast time controls unsuitable for serious improvement, so any tip nudging the
                player toward them — by name or by description, e.g. "a few 3-minute games" or "some
                speed chess" — should be flagged regardless of how reasonable it otherwise sounds.
                Judge only the tip's own words: the player mentioning blitz or bullet in their own
                notes is not the tip recommending it. Rapid and classical games are exactly what this
                app wants players to play — a tip recommending a rapid or classical game is never a
                violation of this criterion, however short the session it suggests.
                5. Does it just restate what the player said they wished they'd done differently, rather
                than building on it? A tip that hands their own words back to them adds nothing. Only
                mark this true if the notes above actually contain a "What they wish they'd done"
                line — if there isn't one, there's nothing for the tip to repeat, so this must be
                false regardless of what the tip suggests. Don't infer an unstated wish from a goal
                simply falling short of its target.
                6. If a "Proposed focus" line is given: is that focus backed by the data above, meaning
                a weak Lichess puzzle theme listed there, or something the player wrote about their own
                chess? Mark true if it is, or if no focus was proposed. Mark false if it's invented,
                generic, or about something the data never mentions.
                7. Is the tip specific to this player? It must refer to something in the data above (a
                goal, a number, a puzzle theme, a session, something they wrote). Advice that would
                fit any player, like "keep up the good work" or "stay consistent", is not specific.
                8. Is it clear what the player should do? Someone reading only the tip must be able to
                picture the exact action. Mark false if it's confusing or garbled (e.g. it mixes two
                separate facts from the data into one instruction), or refers to something like "your
                weakest theme" without naming it.

                If you're not confident on criteria 1–3, 7 or 8, mark it false rather than guessing favorably.
                Criteria 4, 5 and 6 are factual questions about the tip's wording, not judgement calls —
                mark them true only when the tip actually does what they describe, not out of caution.

                Write out your reasoning about all eight criteria first, before scoring any of them — your
                scores should follow from that reasoning, not the other way around. Start by quoting, word
                for word, the tip's own claims about the player and the data lines above that back each
                one. If a claim has no line behind it, the tip is not sound. Describe only what the tip
                actually says, never what it could have said.
                """,
            )
            if (languageName != null) {
                instructions += "\n\n" + unwrap(
                    """
                    The tip and the context above may be written in $languageName rather than English —
                    judge all eight criteria in that language, not against English phrasing. Write your
                    own "reason" field in English regardless, since this reasoning is developer-facing.
                    """,
                )
            }
            return instructions
        }

        private val BANNED_TIME_CONTROLS = listOf("blitz", "bullet")

        /** A hard product rule, so it doesn't rest on the model obeying an instruction. */
        fun bannedTimeControl(tip: String): String? = tip.lowercase().let { text -> BANNED_TIME_CONTROLS.firstOrNull { it in text } }

        /** The player's own wish handed back verbatim. The length floor spares short, generic wishes. */
        fun echoesWishIDone(tip: String, wishIDone: String?): Boolean {
            val normalized = wishIDone?.trim()?.lowercase() ?: return false
            if (normalized.length < 15) return false
            return normalized in tip.lowercase()
        }

        /** A "repeats the wish" verdict means nothing when there's no wish to repeat (a real iOS eval failure). */
        fun reallyRepeatsWishIDone(wishIDone: String?, verdictSaysRepeats: Boolean): Boolean = !wishIDone.isNullOrEmpty() && verdictSaysRepeats

        /** The focus to offer beside an approved tip: dropped (the tip stays) when ungrounded or banned. */
        fun acceptedFocus(suggestion: WeeklyTipSuggestion, verdict: WeeklyTipVerdict): String? =
            if (verdict.focusIsGrounded) acceptableFocus(suggestion.focus) else null

        fun acceptableFocus(focus: String?): String? {
            val normalized = normalizedFocus(focus) ?: return null
            return normalized.takeIf { text -> BANNED_TIME_CONTROLS.none { it in text.lowercase() } }
        }

        /** Blank and placeholder ("none", "n/a"…) mean no focus: the model fills empty optionals that way. */
        fun normalizedFocus(focus: String?): String? {
            val trimmed = focus?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return trimmed.takeIf { it.lowercase() !in setOf("none", "n/a", "null", "nil") }
        }

        fun buildContext(
            plan: WeeklyPlan,
            notes: List<String>,
            recentSessions: List<String>,
            puzzleStatus: LichessPuzzleStatus? = null,
            loggedPuzzleCount: Int = 0,
            lastWeekFocus: String? = null,
            lastLesson: String? = null,
            coachingSuggestion: CoachingSuggestion? = null,
        ): String {
            val lines = mutableListOf("Last week's committed plan:")
            plan.weeklyGoals.forEach { lines += "- ${it.activityType.code}: ${targetDescription(it)}" }
            lines += "\nWhat they actually did:"
            plan.weeklyGoals.forEach { lines += "- ${it.activityType.code}: ${progressDescription(it)}${if (it.isCompleted) " — completed" else ""}" }
            lines += "\nNotes:"
            lines += notes.ifEmpty { listOf("- none left this week") }
            lines += "\nTheir 2 most recent training sessions (may predate or overlap the reviewed week):"
            lines += recentSessions.ifEmpty { listOf("- no recent sessions on record") }
            // Left out entirely (not a "no data" line) with no Lichess account or a failed fetch.
            puzzleStatus?.let {
                lines += "\nLichess puzzles (last ${LichessPuzzleStatus.DAYS} days):"
                lines += puzzleStatusLines(it, loggedPuzzleCount)
            }
            lastWeekFocus?.takeIf { it.isNotEmpty() }?.let { lines += "\nThe focus they chose for this week: $it" }
            lastLesson?.let { lines += "\n$it" }
            coachingSuggestion?.let { lines += it.contextLines }
            return lines.joinToString("\n")
        }

        fun puzzleStatusLines(status: LichessPuzzleStatus, loggedPuzzleCount: Int): List<String> = when (status) {
            LichessPuzzleStatus.NoPuzzles -> {
                val logged = if (loggedPuzzleCount > 0) {
                    "they did log $loggedPuzzleCount puzzles in this app in the same period, so they may solve them elsewhere"
                } else {
                    "and none logged in this app either"
                }
                listOf("- none solved on Lichess ($logged)")
            }
            is LichessPuzzleStatus.Played -> buildList {
                add("- ${status.attempts} puzzles, ${status.firstTryPercent}% solved on the first try, performance ${status.performance} (average puzzle rating ${status.averagePuzzleRating})")
                status.weakness?.let {
                    add("- weakest theme: ${it.themeName} — ${it.attempts} attempts, performing about ${-it.performanceDelta} points below their overall puzzle performance")
                }
            }
        }

        /** What the latest coaching lesson with topics covered, so a tip can reinforce it. */
        fun lastLessonLine(activities: List<Activity>, zone: ZoneId = ZoneId.systemDefault()): String? {
            val lesson = activities.filter { it.activityType.code == ActivityType.COACHING && it.coachingTopics.isNotEmpty() }
                .maxByOrNull { it.startedAt } ?: return null
            val topics = lesson.coachingTopics.joinToString(", ") { if (it == CoachingTopic.ANALYZING) "game analysis" else it.code }
            return "Their latest coaching lesson (${promptDate(lesson.startedAt, zone)}) covered: $topics"
        }

        fun loggedPuzzleCount(activities: List<Activity>): Int =
            activities.filter { it.activityType.code == ActivityType.PUZZLES }.sumOf { it.count ?: 0 }

        /**
         * The [limit] most recent sittings (segments sharing a `sessionId`), newest first, each with
         * every activity it touched: counts where there are any, else minutes.
         */
        fun recentSessionLines(activities: List<Activity>, limit: Int = 2, zone: ZoneId = ZoneId.systemDefault()): List<String> =
            activities.groupBy { it.sessionId ?: it.id }.values
                .sortedByDescending { segments -> segments.mapNotNull { it.endedAt }.maxOrNull() }
                .take(limit)
                .map { segments ->
                    val date = segments.mapNotNull { it.endedAt }.maxOrNull()?.let { promptDate(it, zone) } ?: "unknown date"
                    val parts = segments.groupBy { it.activityType.code }.map { (code, forType) ->
                        val count = forType.sumOf { it.count ?: 0 }
                        if (count > 0) "$code: $count" else "$code: ${maxOf(1, forType.sumOf { it.duration } / 60)} min"
                    }.sorted()
                    "- $date — ${parts.joinToString(", ")}"
                }

        /** Everything the player wrote, review and session notes alike. */
        fun noteLines(review: WeeklyReview?, notedActivities: List<Activity>): List<String> = buildList {
            review?.wentWell?.takeIf { it.isNotEmpty() }?.let { add("- What went well: $it") }
            review?.heldBack?.takeIf { it.isNotEmpty() }?.let { add("- What held them back: $it") }
            review?.wishIDone?.takeIf { it.isNotEmpty() }?.let { add("- What they wish they'd done: $it") }
            review?.nextWeekIntent?.takeIf { it.isNotEmpty() }?.let { add("- Stated intent for the coming week: $it") }
            notedActivities.forEach { activity -> activity.notes?.takeIf { it.isNotEmpty() }?.let { add("- ${activity.activityType.code}: $it") } }
        }

        fun verificationPrompt(context: String, tip: WeeklyTipSuggestion): String {
            val lines = mutableListOf(context, "\nProposed tip to review:", "- ${tip.tip}")
            tip.activityTypeCode?.let { code ->
                lines += tip.suggestedTargetValue?.let { "- Suggested change: $code → $it" } ?: "- About: $code"
            }
            normalizedFocus(tip.focus)?.let { lines += "- Proposed focus for the coming week: $it" }
            return lines.joinToString("\n")
        }

        /** "50 Puzzles", "1 hr 30 min of Openings": English, like the rest of the prompt. */
        fun targetDescription(goal: WeeklyGoal): String = when (goal.targetType) {
            TargetType.COUNT -> "${goal.targetValue} ${promptActivityName(goal.activityType.code)}"
            TargetType.DURATION -> "${promptDuration(goal.targetValue)} of ${promptActivityName(goal.activityType.code)}"
            TargetType.PROGRESS -> goal.resource?.let { "${goal.targetValue}% of ${it.title}" } ?: "${goal.targetValue}% progress"
        }

        fun progressDescription(goal: WeeklyGoal): String = when (goal.targetType) {
            TargetType.COUNT -> "${goal.currentValue}/${goal.targetValue}"
            TargetType.DURATION -> "${WeeklyGoal.displayMinutes(goal.currentValue, goal.targetValue)}m/${goal.targetValue / 60}m"
            TargetType.PROGRESS -> "${goal.currentValue}%/${goal.targetValue}%"
        }

        private fun promptDuration(seconds: Int): String {
            val minutes = seconds / 60
            val hours = minutes / 60
            val rest = minutes % 60
            return when {
                hours > 0 && rest > 0 -> "$hours hr $rest min"
                hours > 0 -> "$hours hr"
                else -> "$minutes min"
            }
        }

        private fun promptDate(instant: java.time.Instant, zone: ZoneId): String =
            instant.atZone(zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US))
    }
}

/** What the tip card shows, after the pipeline and the app's own reading of the suggestion. */
@Serializable
data class WeeklyTip(
    val text: String,
    val focus: String? = null,
    @SerialName("targetCode") val targetActivityTypeCode: String? = null,
    val targetValue: Int? = null,
    val suggestsCoach: Boolean = false,
)
