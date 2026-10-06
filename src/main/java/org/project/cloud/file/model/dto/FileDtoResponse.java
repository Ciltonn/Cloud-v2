package org.project.cloud.file.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.project.cloud.file.model.FileType;
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileDtoResponse {
    private String path;
    private String name;
    private Long size;
    private FileType fileType;

    public FileDtoResponse(String path, String name, FileType fileType) {
        this.path = path;
        this.name = name;
        this.fileType = fileType;
    }
}
