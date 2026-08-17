package io.github.jaffe2718.petprofile.util;

import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;

import java.util.List;
import java.util.Locale;

public final class TaxonomyUtil {
    private TaxonomyUtil() {
    }

    public static boolean sameMajorTaxonomy(ProfileEntity a, ProfileEntity b) {
        if (a == null || b == null) {
            return false;
        }
        return eq(a.kingdom, b.kingdom)
                && eq(a.phylum, b.phylum)
                && eq(a.taxClass, b.taxClass)
                && eq(a.taxOrder, b.taxOrder)
                && eq(a.family, b.family);
    }

    private static boolean eq(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String shortDisplay(ProfileEntity profile) {
        if (profile == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendInitial(builder, profile.kingdom);
        appendInitial(builder, profile.phylum);
        appendInitial(builder, profile.taxClass);
        appendInitial(builder, profile.taxOrder);
        appendInitial(builder, profile.family);
        appendPart(builder, profile.genus);
        appendPart(builder, profile.species);
        appendPart(builder, profile.subspecies);
        return builder.toString().trim();
    }

    public static String speciesDisplay(ProfileEntity profile) {
        if (profile == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendPart(builder, profile.genus);
        appendPart(builder, profile.species);
        appendPart(builder, profile.subspecies);
        return builder.toString().trim();
    }

    private static void appendInitial(StringBuilder builder, String value) {
        String part = value == null ? "" : value.trim();
        if (!part.isEmpty()) {
            builder.append(Character.toUpperCase(part.charAt(0))).append(". ");
        }
    }

    private static void appendPart(StringBuilder builder, String value) {
        String part = value == null ? "" : value.trim();
        if (!part.isEmpty()) {
            builder.append(part).append(' ');
        }
    }

    public static String displayName(ProfileEntity profile, List<ProfileCustomFieldEntity> fields) {
        if (fields != null) {
            for (ProfileCustomFieldEntity field : fields) {
                if ("nickname".equalsIgnoreCase(field.fieldKey)
                        || "昵称".equals(field.fieldName)
                        || "nickname".equalsIgnoreCase(field.fieldName)) {
                    if (field.textValue != null && !field.textValue.trim().isEmpty()) {
                        return field.textValue.trim();
                    }
                }
            }
        }
        String scientific = speciesDisplay(profile);
        if (!scientific.isEmpty()) {
            return scientific;
        }
        return profile.id;
    }

    public static String fullDisplay(ProfileEntity profile) {
        if (profile == null) {
            return "";
        }
        return join(profile.kingdom, profile.phylum, profile.taxClass,
                profile.taxOrder, profile.family, profile.genus,
                profile.species, profile.subspecies);
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }
}
