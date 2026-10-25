package com.atelier.media.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules for restaurant slugs (the /r/{slug} guest URL and the S3 key namespace).
 * Mirrors the CHECK constraint in db/migration/postgresql/V2.
 */
public final class SlugPolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 60;
    public static final String LEGACY_SLUG = "legacy-unassigned";

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{1,58}[a-z0-9]$");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Set<String> RESERVED = Set.of(
            "api", "admin", "auth", "studio", "login", "logout", "register", "signup", "signin",
            "settings", "static", "assets", "www", "app", "menu", "new", "help", "support", "r",
            LEGACY_SLUG);

    private SlugPolicy() {
    }

    /** "Bistro Lumière & Co." -> "bistro-lumiere-and-co". Never returns null. */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String s = MARKS.matcher(Normalizer.normalize(input.strip(), Normalizer.Form.NFKD)).replaceAll("");
        s = s.toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return s;
    }

    /** @return a human-readable problem, or {@code null} when the (normalized) slug may be registered */
    public static String problem(String slug) {
        if (slug == null || slug.length() < MIN_LENGTH) {
            return "Use at least " + MIN_LENGTH + " letters or digits.";
        }
        if (slug.length() > MAX_LENGTH) {
            return "Use at most " + MAX_LENGTH + " characters.";
        }
        if (!FORMAT.matcher(slug).matches()) {
            return "Use lower-case letters, digits and single hyphens only.";
        }
        if (RESERVED.contains(slug)) {
            return "That address is reserved. Please choose another.";
        }
        return null;
    }

    /** Route-level check: could this string be a slug at all? (Reserved slugs still resolve.) */
    public static boolean isWellFormed(String slug) {
        return slug != null && slug.length() <= MAX_LENGTH && FORMAT.matcher(slug).matches();
    }

    public static boolean isReserved(String slug) {
        return RESERVED.contains(slug);
    }
}
