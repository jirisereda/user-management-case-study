package cz.vig.usermanagement;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email) {

    // Trim before bean validation runs, so " a@b.cz " is accepted as "a@b.cz".
    public CreateUserRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip();
    }
}
