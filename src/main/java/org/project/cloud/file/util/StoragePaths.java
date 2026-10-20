package org.project.cloud.file.util;

public class StoragePaths {
    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (path.contains("..")) {
            throw new IllegalArgumentException("Path cannot contain '..'");
        }
        if (path.equals("/")) return "";
        return path.startsWith("/") ? path.substring(1) : path;
    }
    public static String buildObjectName(Long userId, String normalizedPath) {
        return String.format("user-%d-files/%s", userId, normalizedPath);
    }
       public static String parentPath(String normalizedPath) {
        String clean = normalizedPath;
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        int idx = clean.lastIndexOf('/');
        return idx > -1 ? clean.substring(0, idx + 1) : "";
    }
    public static String extractName(String normalizedPath) {
        String clean = normalizedPath;
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        int idx = clean.lastIndexOf('/');
        return idx > -1 ? clean.substring(idx + 1) : clean;
    }
    public static boolean isDirectory(String normalizedPath) {
        return normalizedPath.endsWith("/");
    }
}
