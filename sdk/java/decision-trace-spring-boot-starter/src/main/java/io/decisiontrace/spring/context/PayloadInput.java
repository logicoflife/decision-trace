package io.decisiontrace.spring.context;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

record PayloadInput(@NotEmpty Map<String, Object> payload) {
}
