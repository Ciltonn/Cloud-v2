package org.project.cloud.user.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.project.cloud.exception.ConflictException;
import org.project.cloud.user.model.RoleUser;
import org.project.cloud.user.model.User;
import org.project.cloud.user.model.dto.UserDtoRequest;
import org.project.cloud.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;

import static org.springframework.security.core.userdetails.User.builder;

@Slf4j
@Service
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final MinioClient minioClient;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder encoder, MinioClient minioClient) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.minioClient = minioClient;
    }
    @Value("${minio.bucket-name}")
    private String bucketName;

    @Transactional
    public User register(UserDtoRequest userDto) {
        log.info("Registering new user: {}", userDto.getUsername());
        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw new ConflictException("User already exists");
        }
        User user = new User(userDto.getUsername(),
                encoder.encode(userDto.getPassword()),
                RoleUser.USER
        );
        try {
            User saved = userRepository.save(user);
            try {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object("user-" + saved.getId() + "-files/")
                                .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                                .build()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to create user root", e);
            }
            log.info("Registering user with: {}", saved.getId());
            return saved;
        }catch (DataIntegrityViolationException e) {
            throw new ConflictException("User already exists");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        return userRepository.findByUsername(userName)
                .map(user -> builder()
                        .username(user.getUsername())
                        .password(user.getHashPassword())
                        .authorities(user.getRole().getAuthority())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }
}

