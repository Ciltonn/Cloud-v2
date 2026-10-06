package org.project.cloud.user.service;

import lombok.extern.slf4j.Slf4j;
import org.project.cloud.exception.ConflictException;
import org.project.cloud.user.model.RoleUser;
import org.project.cloud.user.model.User;
import org.project.cloud.user.model.dto.UserDtoRequest;
import org.project.cloud.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.core.userdetails.User.builder;

@Slf4j
@Service
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.encoder = encoder;
    }
    @Transactional
    public User register(UserDtoRequest userDto) {
        log.info("Registering new user: {}", userDto.getEmail());
        if(userRepository.existsByEmail(userDto.getEmail())) {
            throw new ConflictException("User already exists");
        }
        User user = new User(userDto.getEmail(),
                encoder.encode(userDto.getPassword()),
                RoleUser.USER
        );
        User saved= userRepository.save(user);
        log.info("Registering user with: {}", saved.getId());
        return saved;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> builder()
                .username(user.getEmail())
                .password(user.getHashPassword())
                .authorities(user.getRole().getAuthority())
                .build())
        .orElseThrow(()-> new UsernameNotFoundException("User not found"));
           }
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }
}

