package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.SharedCourse
import com.knightindustries.knighttraining.core.persistence.entities.SharedCourseEntity
import com.knightindustries.knighttraining.core.persistence.toEntity
import java.util.UUID

private fun SharedCourseEntity.toDomain() =
    SharedCourse(id, resourceId, SharedCourse.Identity(platform, externalId, slug), url, title, createdAt)

private fun SharedCourse.toEntity() =
    SharedCourseEntity(id, resourceId, identity.platform, identity.externalId, identity.slug, url, title, createdAt)

suspend fun TrainingStore.sharedCourse(resourceId: UUID): SharedCourse? = db.libraryDao().sharedCourse(resourceId)?.toDomain()

/**
 * Saves shared links as resources in one transaction (iOS `saveSharedResources`), each course with
 * its `shared_course` row, then links whatever the catalog already has. [sharedCourses] refer to
 * [resources] by `resourceId`. A course already in the library is skipped, resource and all;
 * anything else is always saved. Returns how many resources were inserted.
 */
suspend fun TrainingStore.saveSharedResources(resources: List<Resource>, sharedCourses: List<SharedCourse>): Int {
    if (resources.isEmpty()) return 0
    val coursesByResource = sharedCourses.associateBy { it.resourceId }
    return write {
        val dao = db.libraryDao()
        var inserted = 0
        for (resource in resources) {
            val course = coursesByResource[resource.id]
            if (course != null) {
                // Checked after earlier inserts, so the same course twice in one batch is caught too.
                val identity = course.identity
                if (dao.isCourseInLibrary(identity.platform, identity.externalId, identity.slug, course.title)) continue
                resourceDao.upsert(resource.toEntity())
                dao.insertSharedCourse(course.toEntity())
            } else {
                resourceDao.upsert(resource.toEntity())
            }
            inserted++
        }
        dao.mergeSharedCourses(now())
        inserted
    }
}

/** Links shared courses the catalog now lists: after every share and every new catalog. */
suspend fun TrainingStore.mergeSharedCourses(): Int = db.libraryDao().mergeSharedCourses(now())

suspend fun TrainingStore.deleteCatalogCourses(platform: String) = db.libraryDao().deleteCatalogCourses(platform)
