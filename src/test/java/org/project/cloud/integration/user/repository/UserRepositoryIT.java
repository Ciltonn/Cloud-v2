package org.project.cloud.integration.user.repository;

import org.junit.jupiter.api.Test;
import org.project.cloud.IntegrationTestBase;
import org.project.cloud.user.model.RoleUser;
import org.project.cloud.user.model.User;
import org.project.cloud.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
@Transactional
class UserRepositoryIT extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveUser() {
        User user = new User(null, "testEmail@test", "testPassword", RoleUser.USER);
        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("testEmail@test");
    }

    @Test
    void saveUserDuplicate() {
        User user = new User(null, "testEmail@test", "testPassword", RoleUser.USER);
        User userDuplicate = new User(null, "testEmail@test", "testPassword", RoleUser.USER);
        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(userDuplicate));
    }

    @Test
    void findByEmail() {
        User user = new User(null, "testEmail@test", "testPassword", RoleUser.USER);
        User saved = userRepository.save(user);
        Optional<User> found = userRepository.findByEmail("testEmail@test");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(saved.getEmail());
    }

    @Test
    void existsByEmail() {
        User user = new User(null, "testEmail@test", "testPassword", RoleUser.USER);
        userRepository.save(user);
        boolean exists = userRepository.existsByEmail("testEmail@test");
        assertThat(exists).isTrue();
    }
}