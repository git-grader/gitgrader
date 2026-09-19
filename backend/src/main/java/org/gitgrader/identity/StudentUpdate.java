package org.gitgrader.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Editable instructor-owned fields of a student profile. */
public record StudentUpdate(@NotBlank String studentUsername, @NotBlank String firstName, @NotBlank String lastName,
		@NotBlank @Email String email) {
}
