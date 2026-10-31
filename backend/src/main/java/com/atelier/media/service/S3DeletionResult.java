package com.atelier.media.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

/** S3 acknowledgements, not proof that historical object versions were physically purged. */
public record S3DeletionResult(Set<String> deleted, Map<String, String> failed,
                               Set<String> unknown, boolean listingComplete) {
    public S3DeletionResult {
        deleted = Set.copyOf(deleted);
        failed = Map.copyOf(failed);
        unknown = Set.copyOf(unknown);
    }

    public static S3DeletionResult empty() {
        return new S3DeletionResult(Set.of(), Map.of(), Set.of(), true);
    }

    public boolean complete() { return listingComplete && failed.isEmpty() && unknown.isEmpty(); }

    public static S3DeletionResult combine(Collection<S3DeletionResult> results) {
        Set<String> acknowledged = new LinkedHashSet<>();
        Map<String, String> failures = new LinkedHashMap<>();
        Set<String> uncertain = new LinkedHashSet<>();
        boolean listed = true;
        for (S3DeletionResult result : results) {
            acknowledged.addAll(result.deleted);
            failures.putAll(result.failed);
            uncertain.addAll(result.unknown);
            listed &= result.listingComplete;
        }
        // Conservative if a key appears in more than one response.
        uncertain.forEach(failures::remove);
        acknowledged.removeAll(failures.keySet());
        acknowledged.removeAll(uncertain);
        return new S3DeletionResult(acknowledged, failures, uncertain, listed);
    }

    @Override public String toString() {
        // Do not accidentally log private object keys through record formatting.
        return "deleted=" + deleted.size() + ", failed=" + failed.size() + ", unknown=" + unknown.size()
                + ", listingComplete=" + listingComplete;
    }
}
