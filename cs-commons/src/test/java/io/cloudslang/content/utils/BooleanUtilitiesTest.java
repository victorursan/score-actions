package io.cloudslang.content.utils;

import io.cloudslang.content.constants.ExceptionsValues;

import static org.junit.Assert.*;

/**
 * Created by victor on 04.09.2016.
 */
public class BooleanUtilitiesTest {

    @org.junit.Test
    public void isValidTrue() throws Exception {
        assertTrue(BooleanUtilities.isValid("true"));
        assertTrue(BooleanUtilities.isValid("TrUe"));
        assertTrue(BooleanUtilities.isValid("TRUE"));
        assertTrue(BooleanUtilities.isValid("FALSE"));
        assertTrue(BooleanUtilities.isValid("fALSE"));
        assertTrue(BooleanUtilities.isValid("false"));
    }

    @org.junit.Test
    public void isValidFalse() throws Exception {
        assertFalse(BooleanUtilities.isValid(""));
        assertFalse(BooleanUtilities.isValid("T"));
        assertFalse(BooleanUtilities.isValid("F"));
        assertFalse(BooleanUtilities.isValid("1"));
        assertFalse(BooleanUtilities.isValid("0"));

    }

    @org.junit.Test
    public void toBooleanValid() throws Exception {
        assertTrue(BooleanUtilities.toBoolean("true"));
        assertTrue(BooleanUtilities.toBoolean("TRUE"));
        assertTrue(BooleanUtilities.toBoolean("tRue"));
        assertTrue(BooleanUtilities.toBoolean("True"));
        assertTrue(BooleanUtilities.toBoolean("", true));
        assertTrue(BooleanUtilities.toBoolean(null, true));
        assertFalse(BooleanUtilities.toBoolean("false"));
        assertFalse(BooleanUtilities.toBoolean("False"));
        assertFalse(BooleanUtilities.toBoolean("fAlSe"));
        assertFalse(BooleanUtilities.toBoolean("FALSE"));
        assertFalse(BooleanUtilities.toBoolean(null, false));
    }

    @org.junit.Test
    public void toBooleanInvalid() throws Exception {
        try {
            BooleanUtilities.toBoolean("a");
        } catch (IllegalArgumentException iae) {
            assertEquals(iae.getMessage(), "a" + ExceptionsValues.EXCEPTION_DELIMITER + ExceptionsValues.INVALID_BOOLEAN_VALUE);
        }
        try {
            BooleanUtilities.toBoolean("b", true);
        } catch (IllegalArgumentException iae) {
            assertEquals(iae.getMessage(), "b" + ExceptionsValues.EXCEPTION_DELIMITER + ExceptionsValues.INVALID_BOOLEAN_VALUE);
        }
    }

}