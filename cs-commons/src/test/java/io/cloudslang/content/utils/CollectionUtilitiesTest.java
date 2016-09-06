package io.cloudslang.content.utils;

import io.cloudslang.content.constants.ExceptionsValues;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by victor on 04.09.2016.
 */
public class CollectionUtilitiesTest {
    @Test
    public void toArrayWithEscaped() throws Exception {
        assertArrayEquals(CollectionUtilities.toArrayWithEscaped("a,b,c,d", ","), new String[]{"a", "b", "c", "d"});
        assertArrayEquals(CollectionUtilities.toArrayWithEscaped("a\\,b\\,c\\,d", ","), new String[]{"a\\", "b\\", "c\\", "d"});
        assertArrayEquals(CollectionUtilities.toArrayWithEscaped(null, ","), new String[0]);

    }

    @Test
    public void toArray() throws Exception {
        assertArrayEquals(CollectionUtilities.toArray("a,b,c,d", ","), new String[]{"a", "b", "c", "d"});
        assertArrayEquals(CollectionUtilities.toArray("a\\,b\\,c,d", ","), new String[]{"a\\,b\\,c", "d"});
        assertArrayEquals(CollectionUtilities.toArray(null, ","), new String[0]);
    }

    @Test
    public void toListWithEscaped() throws Exception {
        assertEquals(CollectionUtilities.toListWithEscaped("a,b,c,d", ","), Arrays.asList("a", "b", "c", "d"));
        assertEquals(CollectionUtilities.toListWithEscaped("a\\,b\\,c\\,d", ","), Arrays.asList("a\\", "b\\", "c\\", "d"));

    }

    @Test
    public void toList() throws Exception {
        assertEquals(CollectionUtilities.toList("a,b,c,d", ","), Arrays.asList("a", "b", "c", "d"));
        assertEquals(CollectionUtilities.toList("a\\,b\\,c,d", ","), Arrays.asList("a\\,b\\,c", "d"));
    }

    private void testInvalidMap(String mapStr, String pairDelimiter, String keyValueDelimiter) {
        try {
            CollectionUtilities.toMap(mapStr, pairDelimiter, keyValueDelimiter);
            assertFalse(true);
        } catch (IllegalArgumentException iae) {
            assertEquals(iae.getMessage(), "a:b:c" + ExceptionsValues.EXCEPTION_DELIMITER + ExceptionsValues.INVALID_KEY_VALUE_PAIR);
        }
    }
    @Test
    public void toMap() throws Exception {
        assertEquals(CollectionUtilities.toMap("a:b,c:d,E:F", ",", ":").get("c"), "d");
        assertEquals(CollectionUtilities.toMap("a::b,c::d,E::F", ",", "::").get("a"), "b");
        assertEquals(CollectionUtilities.toMap("a=b;c=d;E=F", ";", "=").get("E"), "F");
        testInvalidMap("a:b:c,b:c", ",", ":");
    }

}