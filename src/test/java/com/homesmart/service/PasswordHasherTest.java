package com.homesmart.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Test
    void storedHashHasTheDocumentedFormat() {
        String[] parts = PasswordHasher.hash(PASSWORD).split("\\$");

        assertEquals(4, parts.length);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertEquals("600000", parts[1]);
        assertEquals(16, java.util.Base64.getDecoder().decode(parts[2]).length);
        assertEquals(32, java.util.Base64.getDecoder().decode(parts[3]).length);
    }

    @Test
    void verifiesTheRightPasswordOnly() {
        String stored = PasswordHasher.hash(PASSWORD);

        assertTrue(PasswordHasher.verify(PASSWORD, stored));
        assertFalse(PasswordHasher.verify(PASSWORD + "!", stored));
    }

    @Test
    void sameHashInputGivesDifferentSalts() {
        assertNotEquals(PasswordHasher.hash(PASSWORD), PasswordHasher.hash(PASSWORD));
    }

    @Test
    void malformedStoredValuesFailWithoutThrowing() {
        for (String stored : new String[] {null, "", "plain", "md5$1$a$b", "pbkdf2-sha256$x$AA==$AA==",
                "pbkdf2-sha256$600000$!!$AA==", "pbkdf2-sha256$600000$AA==$"}) {
            assertFalse(PasswordHasher.verify(PASSWORD, stored), String.valueOf(stored));
        }
    }

    @Test
    void dummyVerifyReturnsNormally() {
        PasswordHasher.dummyVerify(PASSWORD);
    }
}
