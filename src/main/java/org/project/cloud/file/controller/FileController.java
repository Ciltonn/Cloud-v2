package org.project.cloud.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.project.cloud.file.model.dto.FileDtoResponse;
import org.project.cloud.file.service.FileService;
import org.project.cloud.file.util.StoragePaths;
import org.project.cloud.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@Tag(name = "Files", description = "Работа с файлами")
@Slf4j
@RestController
@RequestMapping("/api/resource")

public class FileController {
    private final UserService userService;
    private final FileService fileService;

    @Autowired
    public FileController(UserService userService, FileService fileService) {
        this.userService = userService;
        this.fileService = fileService;

    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ресурс найден"),
            @ApiResponse(responseCode = "400", description = "Невалидный или отсутствующий путь"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Ресурс не найден"),
            @ApiResponse(responseCode = "500", description = "Неизвестная ошибка")
    })
    @Operation(summary = "Получить информацию о ресурсе")
    @GetMapping
    public ResponseEntity<FileDtoResponse> getResource(@RequestParam String path,
                                                       Authentication authentication) {
        Long userId = userService.findByUsername(authentication.getName()).getId();
        return ResponseEntity.ok(fileService.info(userId, path));
    }

    @Operation(summary = "Удалить ресурс")
    @DeleteMapping
    public ResponseEntity<Void> deleteResource(@RequestParam String path,
                                               Authentication authentication) {
        Long userId = userService.findByUsername(authentication.getName()).getId();
        fileService.delete(userId, path);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Скачать ресурс")
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadResource(@RequestParam String path,
                                                                  Authentication authentication) {
        Long userId = userService.findByUsername(authentication.getName()).getId();
        boolean isDirectory = StoragePaths.isDirectory(path);
        String filename = StoragePaths.extractName(path) + (isDirectory ? ".zip" : "");
        StreamingResponseBody body = outputStream ->
                fileService.download(userId, path, outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    @Operation(summary = "Загрузить ресурс")
    @PostMapping
    public ResponseEntity<List<FileDtoResponse>> uploadResource(
            @RequestParam String path,
            @RequestParam("object") List<MultipartFile> files,
            Authentication authentication) {
        Long userId = userService.findByUsername(authentication.getName()).getId();
        List<FileDtoResponse> responses = fileService.upload(userId, path, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @Operation(summary = "Переместить ресурс")
    @PostMapping("/move")
    public ResponseEntity<FileDtoResponse> moveResource(@RequestParam String from,
                                                        @RequestParam String to,
                                                        Authentication authentication) {
        Long userId = userService.findByUsername(authentication.getName()).getId();
        FileDtoResponse response = fileService.move(userId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @Operation(summary = "Найти ресурс")
    @GetMapping("/search")
    public ResponseEntity<List<FileDtoResponse>> searchResource(@RequestParam String query,
                                                                Authentication authentication) {
        Long userId = userService.findByUsername(authentication.getName()).getId();
        List<FileDtoResponse> responses = fileService.search(userId, query);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responses);
    }
}
