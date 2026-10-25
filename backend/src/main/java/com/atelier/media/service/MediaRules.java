package com.atelier.media.service;

import com.atelier.media.exception.InvalidMediaException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Content-type, extension, size and filename rules shared by every upload path. */
public final class MediaRules {

    /** Accepted photo MIME type -> canonical extension. */
    public static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/webp", "webp",
            "image/jpeg", "jpg",
            "image/png", "png");

    /** Accepted 3D MIME type -> canonical extension. */
    public static final Map<String, String> MODEL_TYPES = Map.of(
            "model/gltf-binary", "glb",
            "model/gltf+json", "gltf");

    /** MIME type -> filename extensions that may legitimately carry it (pre-compression names). */
    private static final Map<String, Set<String>> COMPATIBLE_EXTENSIONS = Map.of(
            "image/webp", Set.of("webp", "jpg", "jpeg", "png", "heic", "heif", "avif"),
            "image/jpeg", Set.of("jpg", "jpeg", "png", "heic", "heif", "webp", "avif"),
            "image/png", Set.of("png"),
            "model/gltf-binary", Set.of("glb"),
            "model/gltf+json", Set.of("gltf"));

    private MediaRules() {
    }

    public static String normalizeContentType(String raw) {
        String ct = raw == null ? "" : raw.toLowerCase(Locale.ROOT).strip();
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi).strip();
        }
        return "image/jpg".equals(ct) ? "image/jpeg" : ct;
    }

    /** @return canonical extension for an accepted photo type */
    public static String requireImage(String contentType) {
        String ext = IMAGE_TYPES.get(contentType);
        if (ext == null) {
            if (MODEL_TYPES.containsKey(contentType)) {
                throw new InvalidMediaException("Create the dish with its photo first, then attach the 3D model with \"Attach 3D model\".");
            }
            throw new InvalidMediaException("Unsupported photo type '" + contentType + "'. Allowed: image/webp, image/jpeg, image/png.");
        }
        return ext;
    }

    /** @return canonical extension for an accepted 3D type */
    public static String requireModel(String contentType) {
        String ext = MODEL_TYPES.get(contentType);
        if (ext == null) {
            throw new InvalidMediaException("3D models must be model/gltf-binary (.glb) or model/gltf+json (.gltf).");
        }
        return ext;
    }

    public static void requireCompatibleExtension(String filename, String contentType) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            if (contentType.startsWith("model/")) {
                throw new InvalidMediaException("3D files must end in .glb or .gltf.");
            }
            return;
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        Set<String> allowed = COMPATIBLE_EXTENSIONS.get(contentType);
        if (allowed == null || !allowed.contains(ext)) {
            throw new InvalidMediaException("File extension '." + ext + "' does not match content type " + contentType + ".");
        }
    }

    public static void requireMaxSize(long bytes, long limit, String what) {
        if (bytes > limit) {
            throw new InvalidMediaException("File is %s; the limit for %s is %s.".formatted(humanBytes(bytes), what, humanBytes(limit)));
        }
    }

    /** Keeps only the last path segment, drops control characters and caps the length. */
    public static String sanitizeFilename(String raw) {
        String name = raw == null ? "" : raw.strip();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("\\p{Cntrl}", "");
        if (name.isBlank()) {
            name = "upload";
        }
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, 200 - ext.length()) + ext;
        }
        return name;
    }

    public static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024.0));
    }
}
