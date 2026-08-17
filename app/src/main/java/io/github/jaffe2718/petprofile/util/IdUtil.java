package io.github.jaffe2718.petprofile.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class IdUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdUtil() {
    }

    public static String randomId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return encode(bytes);
    }

    public static String timeBasedId() {
        byte[] bytes = new byte[16];
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (timestamp >>> (56 - i * 8));
        }
        byte[] randomPart = new byte[8];
        RANDOM.nextBytes(randomPart);
        System.arraycopy(randomPart, 0, bytes, 8, 8);
        return encode(bytes);
    }

    public static String normalizeId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return randomId();
        }
        String trimmed = value.trim();
        if (trimmed.length() == 36 && trimmed.contains("-")) {
            try {
                UUID uuid = UUID.fromString(trimmed);
                ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
                buffer.putLong(uuid.getMostSignificantBits());
                buffer.putLong(uuid.getLeastSignificantBits());
                return encode(buffer.array());
            } catch (IllegalArgumentException ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
