package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.LoginRequest;
import com.snapcal.snapcalbackend.dto.request.SignupRequest;
import com.snapcal.snapcalbackend.dto.request.SocialLoginRequest;
import com.snapcal.snapcalbackend.dto.response.AuthResponse;
import com.snapcal.snapcalbackend.exception.DuplicateEmailException;
import com.snapcal.snapcalbackend.exception.InvalidCredentialsException;
import com.snapcal.snapcalbackend.repository.UserRepository;
import com.snapcal.snapcalbackend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResult signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException();
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getName())
                .build();

        userRepository.save(user);

        return buildAuthResult(user, false);
    }

    @Transactional(readOnly = true)
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResult(user, false);
    }

    @Transactional
    public AuthResult socialLogin(SocialLoginRequest request) {
        SocialUserInfo socialUser = fetchSocialUserInfo(request.getProvider(), request.getAccessToken());

        boolean isNewUser = !userRepository.existsByEmail(socialUser.email());
        User user = userRepository.findByEmail(socialUser.email())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(socialUser.email())
                                .nickname(socialUser.name())
                                .profileImageUrl(socialUser.profileImageUrl())
                                .build()
                ));

        return buildAuthResult(user, isNewUser);
    }

    public AuthResult refresh(String refreshToken) {
        if (!jwtService.isValid(refreshToken)) {
            throw new InvalidCredentialsException();
        }
        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return buildAuthResult(user, false);
    }

    private AuthResult buildAuthResult(User user, boolean isNewUser) {
        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .user(AuthResponse.UserInfo.builder()
                        .userId(user.getId())
                        .name(user.getNickname())
                        .email(user.getEmail())
                        .profileImageUrl(user.getProfileImageUrl())
                        .build())
                .isNewUser(isNewUser ? true : null)
                .build();

        return new AuthResult(response, refreshToken);
    }

    // 소셜 로그인 유저 정보 조회 (provider별 API 호출)
    private SocialUserInfo fetchSocialUserInfo(String provider, String accessToken) {
        return switch (provider) {
            case "kakao" -> fetchKakaoUserInfo(accessToken);
            case "google" -> fetchGoogleUserInfo(accessToken);
            default -> throw new IllegalArgumentException("지원하지 않는 provider: " + provider);
        };
    }

    private SocialUserInfo fetchKakaoUserInfo(String accessToken) {
        // TODO: 카카오 사용자 정보 API 호출
        // GET https://kapi.kakao.com/v2/user/me
        // Authorization: Bearer {accessToken}
        throw new UnsupportedOperationException("카카오 소셜 로그인은 추후 구현 예정입니다.");
    }

    private SocialUserInfo fetchGoogleUserInfo(String accessToken) {
        // TODO: 구글 사용자 정보 API 호출
        // GET https://www.googleapis.com/oauth2/v2/userinfo
        // Authorization: Bearer {accessToken}
        throw new UnsupportedOperationException("구글 소셜 로그인은 추후 구현 예정입니다.");
    }

    public record AuthResult(AuthResponse response, String refreshToken) {}

    private record SocialUserInfo(String email, String name, String profileImageUrl) {}
}
