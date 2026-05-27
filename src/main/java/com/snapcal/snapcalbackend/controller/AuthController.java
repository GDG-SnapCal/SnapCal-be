package com.snapcal.snapcalbackend.controller;

import com.snapcal.snapcalbackend.dto.request.LoginRequest;
import com.snapcal.snapcalbackend.dto.request.RefreshTokenRequest;
import com.snapcal.snapcalbackend.dto.request.SignupRequest;
import com.snapcal.snapcalbackend.dto.request.SocialLoginRequest;
import com.snapcal.snapcalbackend.dto.response.AuthResponse;
import com.snapcal.snapcalbackend.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE = 60 * 60 * 24 * 7; // 7일 (초)

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request,
                               HttpServletResponse response) {
        AuthService.AuthResult result = authService.signup(request);
        setRefreshTokenCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(request);
        setRefreshTokenCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/social")
    public AuthResponse socialLogin(@Valid @RequestBody SocialLoginRequest request,
                                    HttpServletResponse response) {
        AuthService.AuthResult result = authService.socialLogin(request);
        setRefreshTokenCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request,
                                @RequestBody(required = false) RefreshTokenRequest body) {
        String refreshToken = extractRefreshToken(request, body);
        String newAccessToken = authService.refresh(refreshToken);
        return AuthResponse.builder().accessToken(newAccessToken).build();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);   // HTTPS 환경에서만 전송
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        response.addCookie(cookie);
    }

    private String extractRefreshToken(HttpServletRequest request, RefreshTokenRequest body) {
        if (body != null && body.getRefreshToken() != null && !body.getRefreshToken().isBlank()) {
            return body.getRefreshToken();
        }

        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElseThrow(com.snapcal.snapcalbackend.exception.InvalidCredentialsException::new);
        }

        throw new com.snapcal.snapcalbackend.exception.InvalidCredentialsException();
    }
}
