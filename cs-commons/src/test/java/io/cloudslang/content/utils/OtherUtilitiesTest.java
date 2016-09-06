package io.cloudslang.content.utils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by victor on 04.09.2016.
 */
public class OtherUtilitiesTest {
    @Test
    public void isValidEmail() throws Exception {
        assertTrue(OtherUtilities.isValidEmail("a.b@g.com"));
        assertTrue(OtherUtilities.isValidEmail("aaa.bbb@ggg.com"));
        assertTrue(OtherUtilities.isValidEmail("qweqwe@a.com"));
        assertFalse(OtherUtilities.isValidEmail(""));
        assertFalse(OtherUtilities.isValidEmail("a.b@c"));
        assertFalse(OtherUtilities.isValidEmail("qw..eqwe@."));
    }


    @Test
    public void isValidIpPort() throws Exception {
        assertTrue(OtherUtilities.isValidIpPort("9000"));
        assertTrue(OtherUtilities.isValidIpPort("5000"));
        assertFalse(OtherUtilities.isValidIpPort("-1"));
        assertFalse(OtherUtilities.isValidIpPort("9999999999999999999"));
        assertFalse(OtherUtilities.isValidIpPort("65539"));

    }

    @Test
    public void isValidIp() throws Exception {
        assertTrue(OtherUtilities.isValidIp("1.1.1.1"));
        assertTrue(OtherUtilities.isValidIp("255.255.255.255"));
        assertTrue(OtherUtilities.isValidIp("192.168.1.1"));
        assertTrue(OtherUtilities.isValidIp("::1"));
        assertTrue(OtherUtilities.isValidIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
        assertFalse(OtherUtilities.isValidIp(".1.1.1"));
        assertFalse(OtherUtilities.isValidIp("2001:0db8:85a3:0000:0000:8a2e:0370:73349999999"));
        assertFalse(OtherUtilities.isValidIp("2001:0db8:80123---5a3:0000:0000:8a2e:0370:7334"));
    }

    @Test
    public void changeNewLineFromWindowsToUnix() throws Exception {
        assertEquals(OtherUtilities.changeNewLineFromWindowsToUnix("abc\r\ndef"), "abc\ndef");
        assertEquals(OtherUtilities.changeNewLineFromWindowsToUnix("\r\n"), "\n");
        assertEquals(OtherUtilities.changeNewLineFromWindowsToUnix(""), "");
    }

    @Test
    public void changeNewLineFromUnixToWindows() throws Exception {
        assertEquals(OtherUtilities.changeNewLineFromUnixToWindows("abc\ndef"), "abc\r\ndef");
        assertEquals(OtherUtilities.changeNewLineFromUnixToWindows("\n"), "\r\n");
        assertEquals(OtherUtilities.changeNewLineFromUnixToWindows(""), "");
    }

}