package org.project.cloud.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.project.cloud.IntegrationTestBase;
import org.project.cloud.user.model.dto.UserDtoRequest;
import org.project.cloud.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTestIT extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("signUp:200+userName")
    void signUp_success() throws Exception {
        UserDtoRequest request = new UserDtoRequest("test", "password");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userName").value("test"))
                .andExpect(cookie().exists("SESSION"));
        assertThat(userRepository.existsByUserName("test")).isTrue();
    }

    @Test
    @DisplayName("signUp:400")
    void signUp_validation_error() throws Exception {
        UserDtoRequest request = new UserDtoRequest("test2", "password1234567");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.existsByUserName("test2")).isFalse();
    }

    @Test
    @DisplayName("signUp:409")
    void signUp_duplicate_user_name() throws Exception {
        UserDtoRequest request = new UserDtoRequest("test", "password");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userName").value("test"))
                .andExpect(cookie().exists("SESSION"));

        UserDtoRequest requestDuplicate = new UserDtoRequest("test", "password");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDuplicate)))
                .andExpect(status().isConflict());
        assertThat(userRepository.existsByUserName("test")).isTrue();
    }

    @Test
    @DisplayName("signIn:200+userName")
    void signIn() throws Exception {
        UserDtoRequest request = new UserDtoRequest("test", "password");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userName").value("test"))
                .andExpect(cookie().exists("SESSION"));
        assertThat(userRepository.existsByUserName("test")).isTrue();
        UserDtoRequest request1 = new UserDtoRequest("test", "password");
        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("test"))
                .andExpect(cookie().exists("SESSION"));

    }

    @Test
    @DisplayName("signIn:401")
    void signIn_notValid_password() throws Exception {
        UserDtoRequest request = new UserDtoRequest("test", "password");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userName").value("test"))
                .andExpect(cookie().exists("SESSION"));
        assertThat(userRepository.existsByUserName("test")).isTrue();
        UserDtoRequest request1 = new UserDtoRequest("test", "123");
        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isUnauthorized());


    }

    @Test
    @DisplayName("signOut:204")
    void signOut_success() throws Exception {
        UserDtoRequest request = new UserDtoRequest("test", "password");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        UserDtoRequest request1 = new UserDtoRequest("test", "password");
        MvcResult result = mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(post("/api/auth/sign-out").cookie(sessionCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/user/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());

    }
}