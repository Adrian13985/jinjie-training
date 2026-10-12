package com.jinjie.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingPlanTest {
    @Test fun defaultScheduleHasFourWorkouts() {
        val monday = LocalDate.of(2026, 10, 5)
        val schedule = (0L..6L).map { TrainingPlan.defaultFor(monday.plusDays(it)) }
        assertEquals(listOf("LOWER_A", "UPPER_A", "REST", "LOWER_B", "REST", "UPPER_B", "REST"), schedule)
    }

    @Test fun deloadWeekUsesFewerSets() {
        assertEquals(3, TrainingPlan.prescribedSets(3, 8))
        assertEquals(2, TrainingPlan.prescribedSets(3, 9))
        assertEquals(3, TrainingPlan.prescribedSets(3, 10))
    }

    @Test fun allWorkoutsHaveUniqueExercisesAndValidRest() {
        TrainingPlan.workouts.forEach { workout ->
            assertEquals(workout.exercises.size, workout.exercises.map { it.id }.distinct().size)
            assertTrue(workout.exercises.all { it.sets > 0 && it.restSeconds >= 60 })
        }
    }
}
