/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * This should be the only source to get the LocaleDateTime in UTC.
 *
 * @author pnientiedt
 */
public class DateTimeUtility {

    private DateTimeUtility() {
        // private :)
    }

    /**
     * Get LocalDateTime in UTC from given millieseconds
     *
     * @param ts milliseconds
     * @return LocalDateTime
     */
    public static LocalDateTime fromMillis(Long ts) {
        if (ts == null) {
            return null;
        }
        return LocalDateTime.ofEpochSecond(ts / 1000, (int) (ts % 1000) * 1000000, ZoneOffset.UTC);
    }

    /**
     * Returns the current time in UTC.
     *
     * @return the current UTC time
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Returns the current date in UTC.
     *
     * @return the current UTC date
     */
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /**
     *
     * @return the minimum LocalDateTime
     */
    public static LocalDateTime getMin() {
        return LocalDateTime.of(1970, 1, 1, 0, 0, 0);
    }

    /**
     *
     * @return the maximum LocalDateTime
     */
    public static LocalDateTime getMax() {
        return LocalDateTime.of(2222, 1, 1, 0, 0, 0);
    }

    /**
     * Get duration milliseconds between two LocalDateTimes
     *
     * @param start start LodalDateTime
     * @param end end LocalDateTime
     * @return the duration in milliseconds
     */
    public static long getDurationInMillies(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return ChronoUnit.MILLIS.between(start, end);
    }

    /**
     * Get milliseconds from the given date/time.
     *
     * @param dt the date time to get millis from
     * @return the millis
     */
    public static long toMilli(LocalDateTime dt) {
        return Objects.requireNonNull(dt.toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    /**
     * Formats the given date-time to nJAMS default format, i.e.,
     * {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}.
     *
     * @param dt The date to format
     * @return <code>null</code> if <code>null</code> was given.
     */
    public static String toString(LocalDateTime dt) {
        if (dt == null) {
            return null;
        }
        return dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Parses a date-time from a string in nJAMS default format, i.e.,
     * {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}.
     *
     * @param dt The string to parse
     * @return <code>null</code> if <code>null</code> was given.
     */
    public static LocalDateTime fromString(String dt) {
        if (dt == null) {
            return null;
        }
        if (dt.charAt(dt.length() - 1) == 'Z') {
            return LocalDateTime.parse(dt.substring(0, dt.length() - 1), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return LocalDateTime.parse(dt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

}
