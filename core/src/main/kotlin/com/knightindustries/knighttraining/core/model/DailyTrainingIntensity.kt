package com.knightindustries.knighttraining.core.model

/** How hard a day was, for the Daily Activity grid (iOS `DailyTrainingIntensity`). */
enum class DailyTrainingIntensity {
    LIGHT, MODERATE, INTENSE;

    companion object {
        /** Under 10 minutes is no real work; up to an hour light; up to three moderate; beyond, intense. */
        fun of(totalMinutes: Int): DailyTrainingIntensity? = when {
            totalMinutes < 10 -> null
            totalMinutes <= 60 -> LIGHT
            totalMinutes <= 180 -> MODERATE
            else -> INTENSE
        }
    }
}
