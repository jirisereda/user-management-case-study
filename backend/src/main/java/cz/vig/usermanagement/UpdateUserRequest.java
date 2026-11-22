package cz.vig.usermanagement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Only the name can be updated. {@code email} is optional and accepted only if it matches
 * the stored value, so a client sending the full user back is fine, but an attempt to change
 * the email is rejected explicitly instead of being silently ignored.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String name,
        String email) {
}
