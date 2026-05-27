package com.snapcal.snapcalbackend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private UserInfo user;
    private Boolean isNewUser;

    @Getter
    @Builder
    public static class UserInfo {
        private UUID userId;
        private String name;
        private String email;
        private String profileImageUrl;
    }
}
