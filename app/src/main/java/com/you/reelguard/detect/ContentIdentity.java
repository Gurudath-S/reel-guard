package com.you.reelguard.detect;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ContentIdentity {
    public enum Eligibility { ELIGIBLE, EXEMPT, PENDING }
    private static final Pattern IG = Pattern.compile("[a-z0-9._]+");
    private static final Pattern YT = Pattern.compile("[\\p{L}\\p{M}\\p{N}._·-]+");
    private static final Pattern SPACES = Pattern.compile("[\\p{Z}\\t\\n\\x0B\\f\\r\\x85]+");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ContentIdentity() {}

    public static String creatorKey(Platform platform, String handle) {
        if (handle == null) return null;
        String value = Normalizer.normalize(handle.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        if (value.startsWith("@")) value = value.substring(1);
        if (value.length() > 100 || !(platform == Platform.IG ? IG : YT).matcher(value).matches()) return null;
        return platform.key + ":" + (platform == Platform.YT ? "@" : "") + value;
    }

    public static Eligibility eligibility(Platform platform, String creator, Set<String> whitelist) {
        if (creator != null) return whitelist.contains(creator) ? Eligibility.EXEMPT : Eligibility.ELIGIBLE;
        for (String key : whitelist) if (key.startsWith(platform.key + ":")) return Eligibility.PENDING;
        return Eligibility.ELIGIBLE;
    }

    public static String signature(Platform platform, String stableId, String creator, String caption) {
        if (stableId != null && !stableId.trim().isEmpty()) return digest(platform.key, "id", stableId.trim());
        if (creator == null || caption == null) return null;
        String folded = SPACES.matcher(Normalizer.normalize(caption, Normalizer.Form.NFC)).replaceAll(" ").trim();
        if (folded.isEmpty()) return null;
        int end = folded.offsetByCodePoints(0, Math.min(96, folded.codePointCount(0, folded.length())));
        return digest(platform.key, "caption", creator, folded.substring(0, end));
    }

    private static String digest(String... fields) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                sha.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                sha.update(bytes);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte b : sha.digest()) result.append(HEX[(b & 255) >>> 4]).append(HEX[b & 15]);
            return result.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
