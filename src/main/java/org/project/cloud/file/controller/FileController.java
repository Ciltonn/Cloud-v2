package org.project.cloud.file.controller;

import lombok.extern.slf4j.Slf4j;
import org.project.cloud.file.model.dto.FileDtoResponse;
import org.project.cloud.file.service.FileService;
import org.project.cloud.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;


@Slf4j
@RestController
@RequestMapping("/api")

public class FileController {
    private final UserService userService;
    private final FileService fileService;

    @Autowired
    public FileController(FileService fileService, UserService userService) {
        this.fileService = fileService;
        this.userService = userService;
    }

    @GetMapping("/resource")
    public ResponseEntity<FileDtoResponse> getResource(@RequestParam String path,
                                                       Authentication authentication) {
        Long userId = userService.findByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(fileService.info(userId, path));
    }

    @DeleteMapping("/resource")
    public ResponseEntity<Void> deleteResource(@RequestParam String path,
                                               Authentication authentication) {
        Long userId = userService.findByEmail(authentication.getName()).getId();
        fileService.delete(userId, path);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resource/download")
    public ResponseEntity<StreamingResponseBody> downloadResource(@RequestParam String path,
                                                                  Authentication authentication) {
        Long userId = userService.findByEmail(authentication.getName()).getId();
        InputStream stream = fileService.download(userId, path);
        StreamingResponseBody body = outputStream -> {
            try (stream) {
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = stream.read(buffer)) !=-1){
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
    @PostMapping("/resource")
    public ResponseEntity<FileDtoResponse> uploadResource(
            @RequestParam String path,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = userService.findByEmail(authentication.getName()).getId();
        FileDtoResponse response = fileService.upload(userId, path, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
