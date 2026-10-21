package com.sendwise.api.domain.rules;

public final class PhoneNumbers {
    private PhoneNumbers() {
    }

    public record Normalized(String normalized, boolean valid) {
    }

    public static Normalized normalize(String raw) {
        String compact = compact(raw);
        boolean valid = compact.matches("010\\d{8}");
        return new Normalized(valid ? compact : null, valid);
    }

    public static String compact(String raw) {
        if (raw == null) {
            return "";
        }
        String compact = raw.trim().replace(" ", "").replace("-", "");
        if (compact.startsWith("+82")) {
            compact = "0" + compact.substring(3);
        }
        return compact;
    }

    public static boolean sender(String digits) {
        return digits.matches("0\\d{7,10}");
    }

    public static String carrier(String digits) {
        int last = digits.charAt(digits.length() - 1) - '0';
        return switch (Math.floorMod(last, 3)) {
            case 0 -> "SKT";
            case 1 -> "KT";
            default -> "LGU";
        };
    }
}
