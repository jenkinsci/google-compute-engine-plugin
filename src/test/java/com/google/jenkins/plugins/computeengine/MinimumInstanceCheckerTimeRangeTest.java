/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import org.junit.After;
import org.junit.Test;

/**
 * Tests for {@link MinimumInstanceChecker#isActiveTimeRange} — the time range logic that
 * determines whether minimum instance requirements should be enforced based on time of day
 * and day of week. Uses clock injection for deterministic testing.
 */
public class MinimumInstanceCheckerTimeRangeTest {

    @After
    public void resetClock() {
        MinimumInstanceChecker.clock = Clock.systemDefaultZone();
    }

    @Test
    public void nullConfig_alwaysActive() {
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(null));
    }

    @Test
    public void sameDayRange_withinRange_activeDay() {
        var config = timeRange("09:00", "17:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 12, 0); // Tuesday 12:00
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void sameDayRange_withinRange_inactiveDay() {
        var config = timeRange("09:00", "17:00");
        config.setMonday(true);
        config.setTuesday(false);

        setClock(2026, Month.APRIL, 7, 12, 0); // Tuesday 12:00
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void sameDayRange_outsideRange() {
        var config = timeRange("09:00", "17:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 18, 0); // Tuesday 18:00
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void sameDayRange_beforeRange() {
        var config = timeRange("09:00", "17:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 8, 0); // Tuesday 08:00
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void halfOpen_exactFromIncluded() {
        var config = timeRange("09:00", "17:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 9, 0); // Tuesday 09:00 exactly
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void halfOpen_exactToExcluded() {
        var config = timeRange("09:00", "17:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 17, 0); // Tuesday 17:00 exactly
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void halfOpen_oneMinuteBeforeTo() {
        var config = timeRange("09:00", "17:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 16, 59); // Tuesday 16:59
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void midnightCrossing_beforeMidnight() {
        var config = timeRange("22:00", "06:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 23, 0); // Tuesday 23:00
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void midnightCrossing_afterMidnight_attributedToPreviousDay() {
        var config = timeRange("22:00", "06:00");
        config.setTuesday(true);

        // Wednesday 03:00 — post-midnight portion attributed to Tuesday
        setClock(2026, Month.APRIL, 8, 3, 0); // Wednesday
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void midnightCrossing_outsideBothWindows() {
        var config = timeRange("22:00", "06:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 12, 0); // Tuesday 12:00 — gap between 06:00-22:00
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void midnightCrossing_exactToExcluded() {
        var config = timeRange("22:00", "06:00");
        config.setTuesday(true);

        // Wednesday 06:00 exactly — excluded (half-open)
        setClock(2026, Month.APRIL, 8, 6, 0); // Wednesday
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void midnightCrossing_inactiveStartDay() {
        var config = timeRange("22:00", "06:00");
        config.setTuesday(false);
        config.setWednesday(true);

        // Tuesday 23:00 — Tuesday is inactive, even though time is in range
        setClock(2026, Month.APRIL, 7, 23, 0); // Tuesday
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void midnightCrossing_postMidnight_inactiveStartDay() {
        var config = timeRange("22:00", "06:00");
        config.setTuesday(false);
        config.setWednesday(true);

        // Wednesday 03:00 — post-midnight attributed to Tuesday, which is inactive
        setClock(2026, Month.APRIL, 8, 3, 0); // Wednesday
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void midnightCrossing_exactFromIncluded() {
        var config = timeRange("22:00", "06:00");
        config.setTuesday(true);

        setClock(2026, Month.APRIL, 7, 22, 0); // Tuesday 22:00 exactly
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void midnightCrossing_sundayMondayWrap() {
        var config = timeRange("22:00", "06:00");
        config.setSunday(true);

        // Monday 03:00 — post-midnight attributed to Sunday via today.minus(1)
        setClock(2026, Month.APRIL, 6, 3, 0); // Monday
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void weekday_allDaysActive() {
        var config = timeRange("09:00", "17:00");
        config.setMonday(true);
        config.setTuesday(true);
        config.setWednesday(true);
        config.setThursday(true);
        config.setFriday(true);

        setClock(2026, Month.APRIL, 8, 12, 0); // Wednesday
        assertTrue(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    @Test
    public void weekend_inactive() {
        var config = timeRange("09:00", "17:00");
        config.setMonday(true);
        config.setTuesday(true);
        config.setWednesday(true);
        config.setThursday(true);
        config.setFriday(true);
        // Saturday/Sunday not set → inactive

        setClock(2026, Month.APRIL, 11, 12, 0); // Saturday
        assertFalse(MinimumInstanceChecker.isActiveTimeRange(config));
    }

    private MinimumNumberOfInstancesTimeRangeConfig timeRange(String from, String to) {
        var config = new MinimumNumberOfInstancesTimeRangeConfig();
        config.setActiveFrom(from);
        config.setActiveTo(to);
        return config;
    }

    private void setClock(int year, Month month, int day, int hour, int minute) {
        var dateTime = LocalDateTime.of(year, month, day, hour, minute);
        MinimumInstanceChecker.clock =
                Clock.fixed(dateTime.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    }
}
