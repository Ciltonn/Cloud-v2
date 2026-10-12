package org.project.cloud.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.project.cloud.file.model.dto.FileDtoResponse;
import org.project.cloud.file.service.FileService;
import org.project.cloud.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Directory", description = "Работа папками")
@Slf4j
@RestController
@RequestMapping("/api/directory")
public class DirectoryController {
    private final FileService fileService;
    private final UserService userService;

    @Autowired
    public DirectoryController(FileService fileService, UserService userService) {
        this.fileService = fileService;
        this.userService = userService;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ресурс найден"),
            @ApiResponse(responseCode = "400", description = "Невалидный или отсутствующий путь"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Ресурс не найден"),
            @ApiResponse(responseCode = "500", description = "Неизвестная ошибка")
    })
    @Operation(summary = "Получить информацию о папке")
    @GetMapping
    public ResponseEntity<List<FileDtoResponse>> getDirectory(@RequestParam String path,
                                                              Authentication authentication) {
        if (path == null || path.isBlank()) {
            path = "/";
        }
        Long userId = userService.findByUsername(authentication.getName()).getId();
        return ResponseEntity.ok(fileService.getDirectories(userId, path));
    }

    @Operation(summary = "Создать папку")
    @PostMapping
    public ResponseEntity<FileDtoResponse> createDirectory(@RequestParam String path,
                                                           Authentication authentication) {
        Long userId = userService.findByUsername(authentication.getName()).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.createDirectory(userId, path));
    }
}
