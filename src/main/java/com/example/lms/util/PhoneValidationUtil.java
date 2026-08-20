package com.example.lms.util;

import java.util.Map;


public final class PhoneValidationUtil {

    private static final Map<String, int[]> LENGTH_BY_COUNTRY_CODE = Map.ofEntries(
            Map.entry("+1", new int[]{10, 10}),   // US / Canada
            Map.entry("+91", new int[]{10, 10}),  // India
            Map.entry("+44", new int[]{10, 10}),  // UK
            Map.entry("+61", new int[]{9, 9}),    // Australia
            Map.entry("+971", new int[]{9, 9}),   // UAE
            Map.entry("+65", new int[]{8, 8}),    // Singapore
            Map.entry("+49", new int[]{10, 11}),  // Germany
            Map.entry("+33", new int[]{9, 9}),    // France
            Map.entry("+81", new int[]{10, 10}),  // Japan
            Map.entry("+86", new int[]{11, 11})   // China
    );

    private PhoneValidationUtil() {
    }

    public static boolean isSupportedCountryCode(String countryCode) {
        return countryCode != null && LENGTH_BY_COUNTRY_CODE.containsKey(countryCode);
    }

    public static boolean isValid(String countryCode, String phoneNumber) {
        if (countryCode == null || phoneNumber == null) {
            return false;
        }
        int[] range = LENGTH_BY_COUNTRY_CODE.get(countryCode);
        if (range == null) {
            return false; // no silent fallback range - unsupported code is invalid
        }
        int len = phoneNumber.length();
        return len >= range[0] && len <= range[1];
    }
}
