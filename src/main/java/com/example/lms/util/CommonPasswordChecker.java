package com.example.lms.util;

import java.util.Set;


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
