package com.homesmart.service;

import com.homesmart.dao.InMemoryUserDAO;
import com.homesmart.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupServiceTest {

    private static final String CODE = "ABCDEFGH2345";
    private static final String PASSWORD = "a password of fifteen+";

    private final InMemoryUserDAO users = new InMemoryUserDAO();
    private final SetupService service = new SetupService(users, CODE);

    private String rejection(String code, String username, String password) {
        return assertThrows(IllegalArgumentException.class,
                () -> service.createFirstAccount(code, username, password)).getMessage();
    }

    @Test
    void newCodeHas12CrockfordCharacters() {
        assertTrue(SetupService.newCode().matches("[0-9A-HJKMNP-TV-Z]{12}"));
    }

    @Test
    void createsTheAccountWithANormalizedNameAndAHashedPassword() {
        User user = service.createFirstAccount(" abcdefgh2345 ", "  Matt.R ", PASSWORD);

        assertEquals("matt.r", user.username());
        assertTrue(PasswordHasher.verify(PASSWORD, user.passwordHash()));
        assertEquals(1, users.count());
    }

    @Test
    void wrongCodeIsRejectedBeforeAnythingElse() {
        assertEquals(SetupService.WRONG_CODE, rejection("WRONG", "x", "short"));
        assertEquals(SetupService.WRONG_CODE, rejection(null, "matt", PASSWORD));
    }

    @Test
    void nullCodeNeverMatches() {
        SetupService closed = new SetupService(users, null);

        assertEquals(SetupService.WRONG_CODE,
                assertThrows(IllegalArgumentException.class, () -> closed.createFirstAccount(null, "matt", PASSWORD)).getMessage());
    }

    @Test
    void usernameRule() {
        for (String name : new String[] {"ab", "a".repeat(51), "has space", "bad!", "", null}) {
            assertEquals(SetupService.BAD_USERNAME, rejection(CODE, name, PASSWORD), String.valueOf(name));
        }
        service.createFirstAccount(CODE, "a-b_c.9", PASSWORD);
    }

    @Test
    void passwordRuleCountsCodePoints() {
        assertEquals(SetupService.BAD_PASSWORD, rejection(CODE, "matt", "x".repeat(14)));
        assertEquals(SetupService.BAD_PASSWORD, rejection(CODE, "matt", "x".repeat(65)));
        assertEquals(SetupService.BAD_PASSWORD, rejection(CODE, "matt", null));
        // 15 emoji are 30 UTF-16 units but 15 code points
        service.createFirstAccount(CODE, "matt", "\uD83D\uDE00".repeat(15));
    }

    @Test
    void secondAccountIsRefusedEvenWithTheRightCode() {
        service.createFirstAccount(CODE, "matt", PASSWORD);

        assertEquals(SetupService.WRONG_CODE, rejection(CODE, "other", PASSWORD));
        assertEquals(1, users.count());
    }
}
