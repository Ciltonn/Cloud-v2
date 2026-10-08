package org.project.cloud.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.project.cloud.user.model.User;
import org.project.cloud.user.model.dto.UserDtoRequest;
import org.project.cloud.user.model.dto.UserDtoResponse;
import org.project.cloud.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<UserDtoResponse> signUp(@RequestBody @Valid UserDtoRequest userDtoRequest,
                                                  HttpServletRequest httpServletRequest) {
        User user = userService.register(userDtoRequest);
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        userDtoRequest.getUsername(),
                        userDtoRequest.getPassword()
                ));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        httpServletRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDtoResponse(user.getUsername()));
    }

    @PostMapping("/sign-in")
    public ResponseEntity<UserDtoResponse> signIn(@RequestBody @Valid UserDtoRequest userDtoRequest,
                                                  HttpServletRequest httpServletRequest) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        userDtoRequest.getUsername(),
                        userDtoRequest.getPassword()
                ));
        saveSession(httpServletRequest, auth);
        return ResponseEntity.ok(new UserDtoResponse(userDtoRequest.getUsername()));
    }

        @PostMapping("/sign-out")
        public ResponseEntity<Void> signOut (HttpServletRequest httpServletRequest){
            HttpSession session = httpServletRequest.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            return ResponseEntity.noContent().build();
        }

        private void saveSession (HttpServletRequest httpServletRequest, Authentication auth){
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            httpServletRequest.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );
        }
    }

