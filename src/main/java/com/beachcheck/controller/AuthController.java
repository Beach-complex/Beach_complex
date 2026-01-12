package com.beachcheck.controller;

import com.beachcheck.domain.User;
import com.beachcheck.dto.auth.request.LogInRequestDto;
import com.beachcheck.dto.auth.request.RefreshTokenRequestDto;
import com.beachcheck.dto.auth.request.SignUpRequestDto;
import com.beachcheck.dto.auth.response.AuthResponseDto;
import com.beachcheck.dto.auth.response.TokenResponseDto;
import com.beachcheck.dto.auth.response.UserResponseDto;
import com.beachcheck.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponseDto> signUp(@Valid @RequestBody SignUpRequestDto request) {
        UserResponseDto user = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> logIn(@Valid @RequestBody LogInRequestDto request) {
        AuthResponseDto response = authService.logIn(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logOut(@Valid @RequestBody RefreshTokenRequestDto request) {
        authService.logOut(request.refreshToken());
        return ResponseEntity.ok(Map.of("message", "Successfully signed out"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        TokenResponseDto response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser(@AuthenticationPrincipal User user) {
        UserResponseDto response = authService.getCurrentUser(user.getId());
        return ResponseEntity.ok(response);
    }

}
