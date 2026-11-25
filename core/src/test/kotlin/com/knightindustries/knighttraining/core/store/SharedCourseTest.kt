package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.CatalogCourse
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.SharedCourse
import com.knightindustries.knighttraining.core.model.SharedCourse.Identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `SharedCourseIdentityTests`: which course a shared link points at. */
class SharedCourseIdentityTest {
    @Test
    fun chessablesIdComesFromTheUrl() {
        assertEquals(
            Identity(CatalogCourse.CHESSABLE, "306624", null),
            Identity.from("https://www.chessable.com/anna-cramlings-1d4-part-1/course/306624/", null),
        )
    }

    @Test
    fun aChessablePageThatIsntACourseHasNoIdentity() {
        assertNull(Identity.from("https://www.chessable.com/blog/some-post/", null))
        assertNull(Identity.from("https://www.chessable.com/course/not-a-number/", null))
    }

    @Test
    fun chessMoodsIdComesFromTheCoverImage() {
        assertEquals(
            Identity(CatalogCourse.CHESSMOOD, "109", "tactic-ninja"),
            Identity.from("https://chessmood.com/course/tactic-ninja", "https://cdn.chessmood.com/chessmood/images/courses/109/1691479301_64d1ed0524024.webp"),
        )
    }

    @Test
    fun chessMoodWithoutItsImageKeepsOnlyTheSlug() {
        assertEquals(
            Identity(CatalogCourse.CHESSMOOD, null, "tactic-ninja"),
            Identity.from("https://chessmood.com/course/Tactic-Ninja", "https://example.com/courses/109/x.png"),
        )
    }

    @Test
    fun otherHostsHaveNoIdentity() {
        assertNull(Identity.from("https://lichess.org/study/abc", null))
    }
}

/** iOS `SharedCourseDatabaseTests`: saving shared courses, skipping repeats, linking to the catalog. */
class SharedCourseTest : StoreTest() {
    private val courseKind get() = store.resourceKind("course")!!

    private fun catalogCourse(
        platform: String = CatalogCourse.CHESSABLE,
        id: String = "306624",
        title: String = "Anna Cramling's 1.d4 - Part 1",
        author: String? = "WFM Anna Cramling",
        imageUrl: String? = null,
    ) = CatalogCourse(platform = platform, externalId = id, title = title, author = author, imageUrl = imageUrl, fetchedAt = now)

    /** A shared resource and its course, as the share import builds them. */
    private fun shared(
        platform: String = CatalogCourse.CHESSABLE,
        externalId: String? = "306624",
        slug: String? = null,
        title: String = "Anna Cramling's 1.d4 - Part 1",
        author: String? = null,
        imageUrl: String? = null,
    ): Pair<Resource, SharedCourse> {
        val url = "https://www.chessable.com/x/course/${externalId ?: "0"}/"
        val resource = Resource(kind = courseKind, title = title, author = author, url = url, imageUrl = imageUrl)
        return resource to SharedCourse(resourceId = resource.id, identity = Identity(platform, externalId, slug), url = url, title = title)
    }

    private suspend fun save(vararg items: Pair<Resource, SharedCourse>) = store.saveSharedResources(items.map { it.first }, items.map { it.second })

    @Test
    fun sharingACatalogCourseLinksItAndFillsEmptyFields() = test {
        val catalog = catalogCourse(imageUrl = "https://example.com/cover.png")
        store.saveCatalogCourses(listOf(catalog))
        val (resource, course) = shared()

        assertEquals(1, save(resource to course))

        val fetched = store.resource(resource.id)!!
        assertEquals(catalog.id, fetched.catalogCourseId)
        assertEquals("WFM Anna Cramling", fetched.author)
        assertEquals("https://example.com/cover.png", fetched.imageUrl)
        assertEquals(course.identity, store.sharedCourse(resource.id)?.identity)
    }

    /** Browse Catalog hides every imported course, so a linked shared course drops out of it. */
    @Test
    fun aLinkedSharedCourseCountsAsAlreadyInTheLibrary() = test {
        val catalog = catalogCourse()
        store.saveCatalogCourses(listOf(catalog, catalogCourse(id = "999", title = "Some other course")))

        save(shared())

        assertEquals(setOf(catalog.id), store.importedCatalogCourseIds())
    }

    @Test
    fun theMergeNeverOverwritesWhatTheResourceAlreadyHas() = test {
        store.saveCatalogCourses(listOf(catalogCourse(imageUrl = "https://example.com/catalog.png")))
        val (resource, course) = shared(author = "Shared author", imageUrl = "https://example.com/shared.png")

        save(resource to course)

        val fetched = store.resource(resource.id)!!
        assertEquals("Shared author", fetched.author)
        assertEquals("https://example.com/shared.png", fetched.imageUrl)
    }

    @Test
    fun aCourseTheCatalogLacksIsLinkedOnceTheCatalogHasIt() = test {
        val (resource, course) = shared()
        save(resource to course)
        assertNull(store.resource(resource.id)!!.catalogCourseId)

        val catalog = catalogCourse()
        store.saveCatalogCourses(listOf(catalog))

        assertEquals(1, store.mergeSharedCourses())
        assertEquals(catalog.id, store.resource(resource.id)!!.catalogCourseId)
    }

    @Test
    fun aCatalogWipeKeepsTheSharedCourseAndRelinksOnRebuild() = test {
        store.saveCatalogCourses(listOf(catalogCourse()))
        val (resource, course) = shared()
        save(resource to course)

        store.deleteCatalogCourses(CatalogCourse.CHESSABLE)
        assertNull(store.resource(resource.id)!!.catalogCourseId)
        assertNotNull(store.sharedCourse(resource.id))

        val rebuilt = catalogCourse()
        store.saveCatalogCourses(listOf(rebuilt))
        store.mergeSharedCourses()

        assertEquals(rebuilt.id, store.resource(resource.id)!!.catalogCourseId)
    }

    @Test
    fun withoutAnIdTheTitleMatchesOnlyWhenUnique() = test {
        val unique = catalogCourse(CatalogCourse.CHESSMOOD, id = "109", title = "Tactic Ninja")
        store.saveCatalogCourses(
            listOf(unique, catalogCourse(CatalogCourse.CHESSMOOD, id = "1", title = "Twins"), catalogCourse(CatalogCourse.CHESSMOOD, id = "2", title = "Twins")),
        )
        val ninja = shared(CatalogCourse.CHESSMOOD, externalId = null, slug = "tactic-ninja", title = "Tactic Ninja")
        val twins = shared(CatalogCourse.CHESSMOOD, externalId = null, slug = "twins", title = "Twins")

        save(ninja, twins)

        assertEquals(unique.id, store.resource(ninja.first.id)!!.catalogCourseId)
        assertNull(store.resource(twins.first.id)!!.catalogCourseId)
    }

    @Test
    fun sharingTheSameCourseAgainIsSkipped() = test {
        save(shared())
        assertEquals(0, save(shared()))
        assertEquals(1, store.allResources().size)
    }

    @Test
    fun theSameCourseTwiceInOneBatchIsSavedOnce() = test {
        assertEquals(1, save(shared(), shared()))
    }

    @Test
    fun sharingACourseAlreadyAddedFromTheCatalogIsSkipped() = test {
        val catalog = catalogCourse()
        store.saveCatalogCourses(listOf(catalog))
        store.saveResource(catalog.toResource(courseKind))

        assertEquals(0, save(shared()))
    }

    @Test
    fun aSlugOnlyChessMoodCourseIsSkippedBySlug() = test {
        save(shared(CatalogCourse.CHESSMOOD, externalId = null, slug = "tactic-ninja", title = "Tactic Ninja"))
        assertEquals(0, save(shared(CatalogCourse.CHESSMOOD, externalId = null, slug = "tactic-ninja", title = "Tactic Ninja")))
    }

    @Test
    fun resourcesWithoutASharedCourseAreAlwaysSaved() = test {
        val links = listOf(Resource(kind = courseKind, title = "Plain link"), Resource(kind = courseKind, title = "Plain link"))
        assertEquals(2, store.saveSharedResources(links, emptyList()))
    }

    @Test
    fun editingTheResourceKeepsItsSharedCourse() = test {
        val (resource, course) = shared()
        save(resource to course)

        store.saveResource(resource.copy(notes = "Edited"))

        assertEquals(course.id, store.sharedCourse(resource.id)?.id)
    }

    @Test
    fun deletingTheResourceDeletesItsSharedCourseSoItCanBeSharedAgain() = test {
        val (resource, course) = shared()
        save(resource to course)
        store.deleteResource(resource.id)

        assertEquals(1, save(shared()))
    }
}
