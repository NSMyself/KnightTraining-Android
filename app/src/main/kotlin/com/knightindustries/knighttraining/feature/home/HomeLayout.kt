package com.knightindustries.knighttraining.feature.home

/**
 * A Home tile below the pinned plan card. Raw values match iOS's `HomeTile` and are persisted:
 * never rename one, since saved layouts drop ids they don't know.
 */
enum class HomeTile(val id: String, val isWide: Boolean) {
    WEEKLY_ACTIVITY("weeklyActivity", true),
    MILESTONES("milestones", true),
    LAST_WEEK("lastWeek", true),
    COMPLETION_RATE("completionRate", false),
    PUZZLES("puzzles", false),
    MOST_TRAINED("mostTrained", false),
    CLASSICAL_GAMES("classicalGames", false),
    WEEKLY_TOTAL("weeklyTotal", false),
    ALL_TIME_TOTAL("allTimeTotal", false),
    CURRENT_RATING("currentRating", false),
    LAST_STUDIED("lastStudied", false),
    ;

    companion object {
        fun fromId(id: String): HomeTile? = entries.firstOrNull { it.id == id }
    }
}

/** The user's chosen tiles, in order; no duplicates. */
data class HomeLayout(val tiles: List<HomeTile>) {
    init {
        require(tiles.distinct().size == tiles.size)
    }

    val ids: List<String> get() = tiles.map { it.id }

    val hiddenTiles: List<HomeTile> get() = HomeTile.entries.filter { it !in tiles }

    fun adding(tile: HomeTile): HomeLayout = if (tile in tiles) this else HomeLayout(tiles + tile)

    fun removing(tile: HomeTile): HomeLayout = HomeLayout(tiles - tile)

    fun moving(from: Int, to: Int): HomeLayout = HomeLayout(tiles.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) })

    /** Wide tiles take a row; a run of consecutive small tiles shares a two-column grid. */
    val sections: List<Section>
        get() = buildList {
            for (tile in tiles) {
                val last = lastOrNull()
                if (!tile.isWide && last is Section.Small) set(size - 1, Section.Small(last.tiles + tile))
                else add(if (tile.isWide) Section.Wide(tile) else Section.Small(listOf(tile)))
            }
        }

    sealed interface Section {
        data class Wide(val tile: HomeTile) : Section
        data class Small(val tiles: List<HomeTile>) : Section
    }

    companion object {
        /** Home as it was before it was customizable. */
        val DEFAULT = HomeLayout(listOf(HomeTile.WEEKLY_ACTIVITY, HomeTile.MILESTONES, HomeTile.COMPLETION_RATE, HomeTile.PUZZLES, HomeTile.MOST_TRAINED))

        /** Unknown ids (a tile from a newer version) and repeats are dropped. */
        fun fromIds(ids: List<String>): HomeLayout = HomeLayout(ids.mapNotNull(HomeTile::fromId).distinct())
    }
}
