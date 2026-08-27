package io.github.jaffe2718.petprofile.util;

public final class FieldValueUtil {
    private FieldValueUtil() {
    }

    /**
     * Formats a numeric attribute value together with its optional unit.
     * Returns an empty string when the value is null, and omits the unit (and the
     * leading space) when the unit is null/blank.
     */
    public static String formatNumeric(Double value, String unit) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (unit != null && !unit.trim().isEmpty()) {
            text += " " + unit.trim();
        }
        return text;
    }
}
