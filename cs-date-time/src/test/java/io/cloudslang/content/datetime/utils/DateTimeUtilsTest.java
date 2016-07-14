package io.cloudslang.content.datetime.utils;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by ursan on 7/8/2016.
 */
public class DateTimeUtilsTest {
    @Test
    public void isUnix() throws Exception {
        assertTrue(DateTimeUtils.isUnix("unix"));
        assertFalse(DateTimeUtils.isUnix(""));
    }

    @Test
    public void isMilliseconds() throws Exception {
        assertTrue(DateTimeUtils.isMilliseconds("S"));
        assertFalse(DateTimeUtils.isMilliseconds("unix"));
    }

    @Test
    public void isDateValid() throws Exception {
        assertTrue(DateTimeUtils.isDateValid("4 juillet 2001 12:08:56 EEST", Locale.FRENCH));
        assertFalse(DateTimeUtils.isDateValid("Wed, Jul 4, '01", Locale.ENGLISH));
    }

    @Test
    public void getLocaleByCountry() throws Exception {
        assertTrue(DateTimeUtils.getLocaleByCountry("en", "us").equals(Locale.US));
    }

    @Test
    public void getJodaOrJavaDate() throws Exception {
        DateTimeZone timeZone = DateTimeZone.forID(Constants.Miscellaneous.GMT);
        DateTime dateTime = DateTimeUtils
                .getJodaOrJavaDate(DateTimeUtils.getDateFormatter("HH:MM", "fr", "FR"), "4 juillet 2001 12:08:56 EEST")
                .withZone(timeZone);
        assertEquals("2001-07-04T09:08:56.000Z", dateTime.toString());  //Eastern European Summer Time is 3 hours ahead of Greenwich Mean Time
    }

    @Test
    public void getDateFormatter() throws Exception {
        assertEquals(DateTimeUtils.getDateFormatter("HH:MM", "en", "us").getLocale(), Locale.US);
        assertEquals(DateTimeUtils.getDateFormatter("", "en", "us").getLocale(),Locale.US);
    }

    @Test
    public void formatWithDefault() throws Exception {
        assertEquals(DateTimeUtils.formatWithDefault("en", "us").getLocale(), Locale.US);
    }
}
