package org.project.cloud.user.controller;

import org.project.cloud.user.model.dto.UserDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
@GetMapping("/me")
    public ResponseEntity<UserDtoResponse> getCurrentUser(Authentication authentication) {
    return ResponseEntity.ok(new UserDtoResponse(authentication.getName()));
}
}
