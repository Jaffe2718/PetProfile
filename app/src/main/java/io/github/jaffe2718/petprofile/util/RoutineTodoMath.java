package io.github.jaffe2718.petprofile.util;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;

/**
 * Pure helpers shared by the Daily Todo screen and the routine notification sync to decide
 * whether a routine is "due today" and whether it is currently relevant / pending.
 */
public final class RoutineTodoMath {
    private RoutineTodoMath() {
    }

    public static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Today's occurrence timestamp for WEEKLY (today at h:m:s) or ONCE (if onceAt is today), else null. */
    public static Long computeDueToday(RoutineEntity routine, long now) {
        if (RoutineEntity.TYPE_WEEKLY.equals(routine.type)) {
            Set<Integer> weekdays = parseWeekdays(routine.weekdays);
            if (weekdays.isEmpty()) {
                for (int i = 0; i < 7; i++) {
                    weekdays.add(i);
                }
            }
            Calendar nowCal = Calendar.getInstance();
            int todayIndex = nowCal.get(Calendar.DAY_OF_WEEK) - 1;
            if (!weekdays.contains(todayIndex)) {
                return null;
            }
            Calendar due = Calendar.getInstance();
            due.set(Calendar.HOUR_OF_DAY, routine.hour);
            due.set(Calendar.MINUTE, routine.minute);
            due.set(Calendar.SECOND, routine.second);
            due.set(Calendar.MILLISECOND, 0);
            return due.getTimeInMillis();
        } else {
            if (routine.onceAt == null) {
                return null;
            }
            Calendar once = Calendar.getInstance();
            once.setTimeInMillis(routine.onceAt);
            Calendar today = Calendar.getInstance();
            if (once.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && once.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                return routine.onceAt;
            }
            return null;
        }
    }

    /** Whether the routine is part of today's todo list (today's occurrence or a CARRY backlog). */
    public static boolean isRelevant(RoutineEntity routine, Long dueToday, long now, long todayStart) {
        if (!routine.completed) {
            if (dueToday != null) {
                return true;
            }
            if (RoutineEntity.POLICY_CARRY.equals(routine.policy)) {
                return RoutineEntity.TYPE_WEEKLY.equals(routine.type)
                        || (routine.onceAt != null && routine.onceAt <= now);
            }
            return false;
        }
        return routine.lastInteractionTime >= todayStart;
    }

    private static Set<Integer> parseWeekdays(String value) {
        Set<Integer> result = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String part : value.split(",")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
