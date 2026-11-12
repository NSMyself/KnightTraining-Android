package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.knightindustries.knighttraining.core.persistence.entities.ActivityTypeEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceKindEntity

@Dao
interface LookupDao {
    /** Written once per install and never revisited, like iOS's `INSERT OR IGNORE`. */
    @Transaction
    suspend fun seedPresetsIfNeeded() {
        insertActivityTypes(Presets.activityTypes)
        insertResourceKinds(Presets.resourceKinds)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivityTypes(rows: List<ActivityTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResourceKinds(rows: List<ResourceKindEntity>)

    @Query("SELECT * FROM activity_type")
    suspend fun allActivityTypes(): List<ActivityTypeEntity>

    @Query("SELECT * FROM resource_kind")
    suspend fun allResourceKinds(): List<ResourceKindEntity>
}
