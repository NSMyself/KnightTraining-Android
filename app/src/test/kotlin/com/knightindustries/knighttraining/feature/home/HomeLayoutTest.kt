package com.knightindustries.knighttraining.feature.home

import com.knightindustries.knighttraining.feature.home.HomeLayout.Section
import com.knightindustries.knighttraining.feature.home.HomeTile.CURRENT_RATING
import com.knightindustries.knighttraining.feature.home.HomeTile.MILESTONES
import com.knightindustries.knighttraining.feature.home.HomeTile.MOST_TRAINED
import com.knightindustries.knighttraining.feature.home.HomeTile.PUZZLES
import com.knightindustries.knighttraining.feature.home.HomeTile.WEEKLY_ACTIVITY
import org.junit.Assert.assertEquals
import org.junit.Test

/** Port of iOS `HomeLayoutTests`' pure cases; persistence is `AppPreferences`'. */
class HomeLayoutTest {
    @Test
    fun theDefaultMatchesHomeBeforeItWasCustomizable() {
        assertEquals(listOf(WEEKLY_ACTIVITY, MILESTONES, HomeTile.COMPLETION_RATE, PUZZLES, MOST_TRAINED), HomeLayout.DEFAULT.tiles)
    }

    @Test
    fun idsMatchIos() {
        assertEquals(
            listOf("weeklyActivity", "milestones", "lastWeek", "completionRate", "puzzles", "mostTrained", "classicalGames", "weeklyTotal", "allTimeTotal", "currentRating", "lastStudied"),
            HomeTile.entries.map { it.id },
        )
    }

    @Test
    fun unknownAndDuplicateTilesAreDropped() {
        assertEquals(listOf(PUZZLES, MILESTONES), HomeLayout.fromIds(listOf("puzzles", "someFutureTile", "milestones", "puzzles")).tiles)
    }

    @Test
    fun addingATileAlreadyOnHomeChangesNothing() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.adding(PUZZLES))
    }

    @Test
    fun hiddenTilesAreEverythingNotOnHome() {
        assertEquals(HomeTile.entries - PUZZLES, HomeLayout(listOf(PUZZLES)).hiddenTiles)
    }

    @Test
    fun movingReorders() {
        assertEquals(listOf(MILESTONES, PUZZLES, WEEKLY_ACTIVITY), HomeLayout(listOf(WEEKLY_ACTIVITY, MILESTONES, PUZZLES)).moving(0, 2).tiles)
    }

    @Test
    fun smallTilesGroupUntilAWideTileBreaksTheRun() {
        val layout = HomeLayout(listOf(PUZZLES, MOST_TRAINED, MILESTONES, CURRENT_RATING, WEEKLY_ACTIVITY))
        assertEquals(
            listOf(Section.Small(listOf(PUZZLES, MOST_TRAINED)), Section.Wide(MILESTONES), Section.Small(listOf(CURRENT_RATING)), Section.Wide(WEEKLY_ACTIVITY)),
            layout.sections,
        )
    }
}
