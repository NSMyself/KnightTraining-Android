package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.ActivityReminder
import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.ReminderSchedule
import com.knightindustries.knighttraining.core.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

/** iOS `ActivityReminderTests`, plus the stored day encoding iOS shares. */
class ActivityReminderTest : StoreTest() {
    private suspend fun makeGoalTemplate(): GoalTemplate {
        val plan = PlanTemplate(name = "Test Plan", isActive = true)
        store.savePlanTemplate(plan)
        val template = GoalTemplate(planTemplateId = plan.id, activityType = type("puzzles"), targetType = TargetType.COUNT, targetValue = 20)
        store.saveGoalTemplate(template)
        return template
    }

    @Test
    fun saveAndFetchAnActivityReminder() = test {
        val template = makeGoalTemplate()
        val reminder = ActivityReminder(goalTemplateId = template.id, schedule = ReminderSchedule(true, setOf(MONDAY, WEDNESDAY, FRIDAY), 7, 30))

        store.saveActivityReminder(reminder)
        val fetched = store.activityReminders(template.id)

        assertEquals(1, fetched.size)
        assertEquals(reminder.id, fetched.single().id)
        assertEquals(reminder.schedule, fetched.single().schedule)
    }

    /** One block, several schedules: the reason reminders are their own table. */
    @Test
    fun aBlockCanCarrySeveralReminders() = test {
        val template = makeGoalTemplate()
        val weekdayMorning = ActivityReminder(goalTemplateId = template.id, schedule = ReminderSchedule(true, setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), 7, 0))
        val saturdayEvening = ActivityReminder(goalTemplateId = template.id, schedule = ReminderSchedule(true, setOf(SATURDAY), 20, 0))

        store.saveActivityReminder(weekdayMorning)
        store.saveActivityReminder(saturdayEvening)

        assertEquals(setOf(weekdayMorning.id, saturdayEvening.id), store.activityReminders(template.id).map { it.id }.toSet())
    }

    @Test
    fun remindersAreScopedToTheirOwnBlock() = test {
        val a = makeGoalTemplate()
        val b = makeGoalTemplate()
        store.saveActivityReminder(ActivityReminder(goalTemplateId = a.id, schedule = ReminderSchedule(true, setOf(MONDAY))))
        store.saveActivityReminder(ActivityReminder(goalTemplateId = b.id, schedule = ReminderSchedule(true, setOf(TUESDAY))))

        val fetched = store.activityReminders(a.id)
        assertEquals(1, fetched.size)
        assertEquals(a.id, fetched.single().goalTemplateId)
    }

    @Test
    fun savingAgainWithTheSameIdEditsInPlace() = test {
        val template = makeGoalTemplate()
        val reminder = ActivityReminder(goalTemplateId = template.id, schedule = ReminderSchedule(true, setOf(MONDAY), 7, 0))
        store.saveActivityReminder(reminder)

        store.saveActivityReminder(reminder.copy(schedule = reminder.schedule.copy(weekdays = setOf(FRIDAY), hour = 20)))

        val fetched = store.activityReminders(template.id)
        assertEquals(1, fetched.size)
        assertEquals(setOf(FRIDAY), fetched.single().schedule.weekdays)
        assertEquals(20, fetched.single().schedule.hour)
    }

    @Test
    fun deleteAnActivityReminder() = test {
        val template = makeGoalTemplate()
        val reminder = ActivityReminder(goalTemplateId = template.id, schedule = ReminderSchedule(true, setOf(MONDAY)))
        store.saveActivityReminder(reminder)

        store.deleteActivityReminder(reminder.id)

        assertTrue(store.activityReminders(template.id).isEmpty())
    }

    /** Reminders last across weeks, but not past the block they're about. */
    @Test
    fun deletingTheBlockDeletesItsReminders() = test {
        val template = makeGoalTemplate()
        store.saveActivityReminder(ActivityReminder(goalTemplateId = template.id, schedule = ReminderSchedule(true, setOf(MONDAY))))

        store.deleteGoalTemplate(template.id)

        assertTrue(store.activityReminders(template.id).isEmpty())
    }

    /** Stored as iOS stores it: `Calendar` weekday numbers, 1 = Sunday. */
    @Test
    fun daysAreStoredInIosNumbering() {
        assertEquals("1,2,7", ActivityReminder.encode(setOf(SATURDAY, SUNDAY, MONDAY)))
        assertEquals(setOf(SUNDAY, MONDAY, SATURDAY), ActivityReminder.decode("1,2,7"))
        assertEquals(setOf(TUESDAY), ActivityReminder.decode("3,9,x"))
    }
}
