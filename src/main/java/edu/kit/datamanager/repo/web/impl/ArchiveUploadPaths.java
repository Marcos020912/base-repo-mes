package edu.kit.datamanager.repo.web.impl;

import java.io.IOException;
import java.util.Set;

/** Shared path checks for ZIP uploads before any entry is persisted. */
final class ArchiveUploadPaths {
    private ArchiveUploadPaths() {}

    static String clean(String path) {
        if (path == null) return null;
        String value = path.replace('\\', '/');
        if (value.isBlank() || value.startsWith("/") || value.matches("^[A-Za-z]:.*")) return null;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character == 0x7f) return null;
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return null;
        }
        return value;
    }

    static void addUnique(Set<String> targets, String path) throws IOException {
        for (String existing : targets) {
            if (existing.equals(path) || existing.startsWith(path + "/") || path.startsWith(existing + "/"))
                throw new IOException("El ZIP contiene nombres repetidos o en conflicto: " + path);
        }
        targets.add(path);
    }
}
