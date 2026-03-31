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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Configuration for limiting the time range during which minimum instance requirements are active.
 * If not configured, minimum instances are always enforced.
 */
public class MinimumNumberOfInstancesTimeRangeConfig {

    private String activeFrom;
    private String activeTo;

    private Boolean monday;
    private Boolean tuesday;
    private Boolean wednesday;
    private Boolean thursday;
    private Boolean friday;
    private Boolean saturday;
    private Boolean sunday;

    @DataBoundConstructor
    public MinimumNumberOfInstancesTimeRangeConfig() {}

    private static LocalTime getLocalTime(String value) {
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
        } catch (DateTimeParseException e) {
            try {
                return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH));
            } catch (DateTimeParseException ignore) {
            }
        }
        return null;
    }

    public static void validateLocalTimeString(String value) {
        if (getLocalTime(value) == null) {
            throw new IllegalArgumentException("Value " + value + " is not valid time format, ([12:34 AM] or [23:45])");
        }
    }

    public String getActiveFrom() {
        return activeFrom;
    }

    @DataBoundSetter
    public void setActiveFrom(String activeFrom) {
        validateLocalTimeString(activeFrom);
        this.activeFrom = activeFrom;
    }

    public LocalTime getActiveFromAsTime() {
        return getLocalTime(activeFrom);
    }

    public String getActiveTo() {
        return activeTo;
    }

    @DataBoundSetter
    public void setActiveTo(String activeTo) {
        validateLocalTimeString(activeTo);
        this.activeTo = activeTo;
    }

    public LocalTime getActiveToAsTime() {
        return getLocalTime(activeTo);
    }

    public Boolean getMonday() {
        return monday;
    }

    @DataBoundSetter
    public void setMonday(Boolean monday) {
        this.monday = monday;
    }

    public Boolean getTuesday() {
        return tuesday;
    }

    @DataBoundSetter
    public void setTuesday(Boolean tuesday) {
        this.tuesday = tuesday;
    }

    public Boolean getWednesday() {
        return wednesday;
    }

    @DataBoundSetter
    public void setWednesday(Boolean wednesday) {
        this.wednesday = wednesday;
    }

    public Boolean getThursday() {
        return thursday;
    }

    @DataBoundSetter
    public void setThursday(Boolean thursday) {
        this.thursday = thursday;
    }

    public Boolean getFriday() {
        return friday;
    }

    @DataBoundSetter
    public void setFriday(Boolean friday) {
        this.friday = friday;
    }

    public Boolean getSaturday() {
        return saturday;
    }

    @DataBoundSetter
    public void setSaturday(Boolean saturday) {
        this.saturday = saturday;
    }

    public Boolean getSunday() {
        return sunday;
    }

    @DataBoundSetter
    public void setSunday(Boolean sunday) {
        this.sunday = sunday;
    }

    public boolean isDayActive(DayOfWeek day) {
        return Boolean.TRUE.equals(
                switch (day) {
                    case MONDAY -> monday;
                    case TUESDAY -> tuesday;
                    case WEDNESDAY -> wednesday;
                    case THURSDAY -> thursday;
                    case FRIDAY -> friday;
                    case SATURDAY -> saturday;
                    case SUNDAY -> sunday;
                });
    }

    public boolean isDayActive(String day) {
        return isDayActive(DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT)));
    }
}
