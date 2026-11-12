package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.CatalogCourse
import com.knightindustries.knighttraining.core.model.CatalogCourse.LanguageSection
import com.knightindustries.knighttraining.core.model.Resource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports of iOS `ResourceTests`, `CatalogCourseLanguageTests` and `CatalogCourseDetailsTests`. */
class LibraryTest : StoreTest() {
    private val book get() = store.resourceKind("book")!!

    private suspend fun saved(title: String = "My System", progress: Int = 0) =
        Resource(kind = book, title = title, progressPercentage = progress).also { store.saveResource(it) }

    // MARK: - Resources

    @Test
    fun allResourcesAreSortedByTitleIgnoringCase() = test {
        saved("zwischenzug"); saved("Endgame Manual"); saved("amateur to IM")
        assertEquals(listOf("amateur to IM", "Endgame Manual", "zwischenzug"), store.allResources().map { it.title })
    }

    @Test
    fun searchMatchesTitleAuthorAndNotes() = test {
        store.saveResource(Resource(kind = book, title = "My System", author = "Nimzowitsch"))
        store.saveResource(Resource(kind = book, title = "Endgame Manual", notes = "Rook endings first"))
        assertEquals(listOf("My System"), store.searchResources("nimzo").map { it.title })
        assertEquals(listOf("Endgame Manual"), store.searchResources("rook").map { it.title })
    }

    @Test
    fun deletingAResourceCascadesItsCompletionsAndDetachesActivities() = test {
        val resource = saved()
        store.recordCompletion(resource.id)
        val activity = Activity(activityType = type("openings"), resource = resource, startedAt = now, duration = 600)
        store.saveActivity(activity)

        store.deleteResource(resource.id)

        assertNull(store.resource(resource.id))
        assertTrue(store.completions(resource.id).isEmpty())
        assertNull(store.activity(activity.id)!!.resource)
    }

    @Test
    fun editingAResourceKeepsItsCompletionsAndActivities() = test {
        val resource = saved()
        store.recordCompletion(resource.id)
        val activity = Activity(activityType = type("openings"), resource = resource, startedAt = now, duration = 1800)
        store.saveActivity(activity)

        store.saveResource(resource.withNotes("Edited"))

        assertEquals(1, store.completions(resource.id).size)
        assertEquals(resource.id, store.activity(activity.id)!!.resource?.id)
        assertEquals("Edited", store.resource(resource.id)!!.notes)
    }

    @Test
    fun recordingCompletionsCountsEachPass() = test {
        val resource = saved(progress = 100)
        store.recordCompletion(resource.id, "First pass", 4, 14)
        assertEquals(1, store.resource(resource.id)!!.completionCount)
        now = now.plusSeconds(60)
        store.recordCompletion(resource.id, "Second pass", 5, 7)
        assertEquals(2, store.resource(resource.id)!!.completionCount)
        // Newest first.
        assertEquals(listOf("Second pass", "First pass"), store.completions(resource.id).map { it.notes })
    }

    @Test
    fun completionsBelongToTheirResourceOnly() = test {
        val first = saved("A")
        val second = saved("B")
        store.recordCompletion(first.id, "Mine")
        store.recordCompletion(second.id, "Theirs")
        assertEquals(listOf("Mine"), store.completions(first.id).map { it.notes })
    }

    @Test
    fun markingFinishedSetsFullProgressAndLogsTheCompletion() = test {
        val resource = saved(progress = 60)
        store.markResourceFinished(resource.id, "Done", 5)

        val after = store.resource(resource.id)!!
        assertEquals(100, after.progressPercentage)
        assertEquals(1, after.completionCount)
        assertEquals(now, after.lastAccessedAt)
        assertEquals(5, store.completions(resource.id).single().rating)
    }

    /** A slider oscillating around 100% must never count extra finishes. */
    @Test
    fun aDraggedSliderNeverCountsACompletion() = test {
        val resource = saved(progress = 90)
        listOf(100, 98, 100, 99, 100).forEach { store.setResourceProgress(resource.id, it) }
        val after = store.resource(resource.id)!!
        assertEquals(100, after.progressPercentage)
        assertEquals(0, after.completionCount)
    }

    @Test
    fun restartingResetsProgressButKeepsCompletions() = test {
        val resource = saved(progress = 100)
        store.recordCompletion(resource.id, "First pass")
        store.restartResource(resource.id)

        val restarted = store.resource(resource.id)!!
        assertEquals(0, restarted.progressPercentage)
        assertEquals(1, restarted.completionCount)
        assertEquals(1, store.completions(resource.id).size)
    }

    /** Inclusive at the start, exclusive at the end, judged on start; ongoing never counts. */
    @Test
    fun timeInvestedCountsCompletedSessionsInTheWindow() = test {
        val resource = saved()
        val other = saved("Other")
        val cut = now.minusSeconds(1000)
        fun log(start: java.time.Instant, seconds: Int, on: Resource, ended: Boolean = true) =
            Activity(activityType = type("openings"), resource = on, startedAt = start, endedAt = if (ended) start.plusSeconds(seconds.toLong()) else null, duration = seconds)
        listOf(
            log(now.minusSeconds(3000), 600, resource),
            log(cut, 300, resource),
            log(now, 900, resource),
            log(cut, 999, other),
            log(cut, 111, resource, ended = false),
        ).forEach { store.saveActivity(it) }

        assertEquals(600, store.timeInvested(resource.id, end = cut))
        assertEquals(300, store.timeInvested(resource.id, cut, now))
        assertEquals(1800, store.timeInvested(resource.id))
    }

    @Test
    fun finishedCountMatchesCompletedResources() = test {
        saved("A", 100); saved("B", 40); saved("C", 100)
        assertEquals(2, store.finishedResourceCount())
    }

    // MARK: - Catalog

    private fun chessmood(id: String = "12", url: String? = "https://chessmood.com/course/endgame-roadmap") = CatalogCourse(
        platform = CatalogCourse.CHESSMOOD, externalId = id, title = "Endgame Roadmap", url = url,
        courseDescription = "Your compass in the endgame.", topicCode = "endgames", minRating = 1400, maxRating = 2000, lengthMinutes = 600,
    )

    @Test
    fun catalogDetailsAndLanguagesRoundTrip() = test {
        store.saveCatalogCourses(listOf(chessmood(), CatalogCourse(platform = CatalogCourse.CHESSABLE, externalId = "1", title = "T", languages = listOf("es", "en"))))
        val courses = store.catalogCourses()
        assertEquals(chessmood().copy(id = courses.first { it.isChessmood }.id, fetchedAt = courses.first { it.isChessmood }.fetchedAt), courses.first { it.isChessmood })
        assertEquals(listOf("es", "en"), courses.first { !it.isChessmood }.languages)
    }

    /** A resync updates rows in place, keeping their ids, so library links survive. */
    @Test
    fun aResyncKeepsEachCoursesIdAndTheLibraryLink() = test {
        store.saveCatalogCourses(listOf(chessmood()))
        val first = store.catalogCourse(CatalogCourse.CHESSMOOD, "12")!!
        val inLibrary = store.addCatalogCourse(first)

        store.saveCatalogCourses(listOf(chessmood().copy(title = "Endgame Roadmap 2.0", lengthMinutes = 700)))

        val resynced = store.catalogCourse(CatalogCourse.CHESSMOOD, "12")!!
        assertEquals(first.id, resynced.id)
        assertEquals("Endgame Roadmap 2.0", resynced.title)
        assertEquals(first.id, store.resource(inLibrary.id)!!.catalogCourseId)
        assertEquals(setOf(first.id), store.importedCatalogCourseIds())
    }

    @Test
    fun aCopiedCourseKeepsItsDescription() = test {
        assertEquals("Your compass in the endgame.", chessmood().toResource(store.resourceKind("course")!!).description)
    }

    @Test
    fun onlyOpeningsEndgamesAndTacticsMapToABlock() {
        assertEquals("openings", CatalogCourse.topicCode("openings"))
        assertEquals("puzzles", CatalogCourse.topicCode("Tactics"))
        assertNull(CatalogCourse.topicCode("strategy"))
        assertNull(CatalogCourse.topicCode(null))
    }

    @Test
    fun referralLinks() {
        assertEquals("https://chessmood.com/course/endgame-roadmap?r=KnightTraining", chessmood().referralUrl)
        assertEquals(
            "https://chessmood.com/course/x?ref=nav&r=KnightTraining",
            chessmood(url = "https://chessmood.com/course/x?r=someoneElse&ref=nav").referralUrl,
        )
        assertEquals(CatalogCourse.CHESSMOOD_REFERRAL_URL, chessmood(url = null).referralUrl)
        assertNull(CatalogCourse(platform = CatalogCourse.CHESSABLE, externalId = "1", title = "T", url = "https://chessable.com/x").referralUrl)
    }

    // MARK: - Language sections

    private fun course(id: String, languages: List<String>) =
        CatalogCourse(platform = CatalogCourse.CHESSABLE, externalId = id, title = id, languages = languages)

    private fun sections(courses: List<CatalogCourse>, preferred: String?) =
        CatalogCourse.languageSections(courses, preferred).map { (section, list) -> section to list.map { it.externalId } }

    @Test
    fun theUsersLanguageComesFirstThenEnglishThenTheRest() {
        val result = sections(listOf(course("en", listOf("en")), course("de", listOf("de")), course("es", listOf("es")), course("fr", listOf("fr"))), "es")
        assertEquals(listOf(LanguageSection.Preferred("es"), LanguageSection.English, LanguageSection.Other), result.map { it.first })
        assertEquals(listOf(listOf("es"), listOf("en"), listOf("de", "fr")), result.map { it.second })
    }

    @Test
    fun aTranslatedCourseAppearsOnceInItsBestSection() {
        val courses = listOf(course("en+es", listOf("en", "es")), course("de+en", listOf("de", "en")))
        assertEquals(listOf(listOf("en+es"), listOf("de+en")), sections(courses, "es").map { it.second })
        assertEquals(listOf(listOf("de+en"), listOf("en+es")), sections(courses, "de").map { it.second })
    }

    @Test
    fun onlyTheBaseLanguageCodeIsCompared() {
        assertEquals(listOf(LanguageSection.Preferred("pt"), LanguageSection.English), sections(listOf(course("1", listOf("pt")), course("2", listOf("en"))), "pt-PT").map { it.first })
    }

    @Test
    fun anEnglishUserGetsEnglishThenTheRestAndEmptySectionsAreLeftOut() {
        val courses = listOf(course("en", listOf("en")), course("es", listOf("es")))
        assertEquals(listOf(LanguageSection.English, LanguageSection.Other), sections(courses, "en-GB").map { it.first })
        assertEquals(listOf(LanguageSection.English, LanguageSection.Other), sections(courses, null).map { it.first })
        assertEquals(listOf(LanguageSection.English, LanguageSection.Other), sections(listOf(course("en", listOf("en")), course("de", listOf("de"))), "pt-PT").map { it.first })
        assertEquals(listOf(LanguageSection.Other), sections(listOf(course("1", emptyList())), "es").map { it.first })
    }

    @Test
    fun eachSectionKeepsTheIncomingOrder() {
        assertEquals(listOf(listOf("b", "a", "c")), sections(listOf(course("b", listOf("en")), course("a", listOf("en")), course("c", listOf("en"))), null).map { it.second })
    }
}
