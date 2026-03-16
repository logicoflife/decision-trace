package io.decisiontrace.spring.context;

import jakarta.validation.constraints.NotBlank;

record EvidenceInput(@NotBlank String key, Object value) {
}
