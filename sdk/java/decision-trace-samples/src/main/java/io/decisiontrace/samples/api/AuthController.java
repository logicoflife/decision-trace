package io.decisiontrace.samples.api;

import io.decisiontrace.samples.service.AuthService;
import io.decisiontrace.samples.service.dto.AuthRequest;
import io.decisiontrace.samples.service.dto.AuthResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@RequestBody AuthRequest request) {
        return authService.login(request);
    }
}
