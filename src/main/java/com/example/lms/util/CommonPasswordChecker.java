package com.example.lms.util;

import java.util.Set;

/**
 * Rejects passwords from a small denylist of commonly used passwords,
 * per SRS Sign Up rule: "Password should not be a commonly used password".
 *
 * For production, swap this static set for a proper breached-password
 * list (e.g. Have I Been Pwned range API or a bundled rockyou-style list).
 * This starter set covers the most common patterns so the rule isn't
 * silently unenforced.
 */
public final class CommonPasswordChecker {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password123",
            "12345678", "123456789", "qwerty123",
            "letmein123", "welcome123", "admin1234",
            "iloveyou1", "abc12345", "Passw0rd!",
            "P@ssw0rd", "Password1!", "Qwerty123!"
    );

    private CommonPasswordChecker() {
    }

    public static boolean isCommon(String password) {
        if (password == null) {
            return false;
        }
        for (String common : COMMON_PASSWORDS) {
            if (common.equalsIgnoreCase(password)) {
                return true;
            }
        }
        return false;
    }
}