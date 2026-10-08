package org.project.cloud.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter
public class UserDtoRequest {
    @NotBlank(message = "Username should not be empty")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;

    @NotBlank(message = "Password should not be empty")
    @Size(min = 3, max = 8, message = "Password must be between 3 and 20 characters")
    private String password;
}
