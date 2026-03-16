package io.decisiontrace.spring.context;

import jakarta.validation.constraints.NotBlank;

record PolicyCheckInput(@NotBlank String policy, @NotBlank String result) {
}
