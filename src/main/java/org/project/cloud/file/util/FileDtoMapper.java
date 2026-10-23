package org.project.cloud.file.util;

import org.project.cloud.file.model.Type;
import org.project.cloud.file.model.dto.FileDtoResponse;

public class FileDtoMapper {
    private FileDtoMapper() {}
    public static FileDtoResponse file(String normalizedPath, long size) {
        return new FileDtoResponse(
                StoragePaths.parentPath(normalizedPath),
                StoragePaths.extractName(normalizedPath),
                size,
                Type.FILE
        );
    }
    public static FileDtoResponse directory(String normalizedPath) {
        return new FileDtoResponse(
                StoragePaths.parentPath(normalizedPath),
                StoragePaths.extractName(normalizedPath),
                Type.DIRECTORY
        );
    }
    public static FileDtoResponse root() {
        return new FileDtoResponse("", "", Type.DIRECTORY);
    }
}
