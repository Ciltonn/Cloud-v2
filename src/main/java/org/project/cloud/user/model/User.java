package org.project.cloud.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Email should not be empty")
    @Email(message = "Email should be valid")
    @Column(name = "email", unique = true, nullable = false, length = 20)
    private String email;

    @NotBlank(message = "Password should not be empty")
    @Column(name = "hash_password", nullable = false, length = 255)
    private String hashPassword;

    @NotNull
    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RoleUser role;

    public User(String userName, String hashPassword, RoleUser role) {
        this.email = userName;
        this.hashPassword = hashPassword;
        this.role = role;
    }
 }
