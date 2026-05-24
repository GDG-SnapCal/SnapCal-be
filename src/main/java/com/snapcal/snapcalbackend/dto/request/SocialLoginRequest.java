package com.snapcal.snapcalbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class SocialLoginRequest {

    @NotBlank(message = "provider를 입력해주세요.")
    @Pattern(regexp = "kakao|google", message = "provider는 kakao 또는 google이어야 합니다.")
    private String provider;

    @NotBlank(message = "accessToken을 입력해주세요.")
    private String accessToken;
}
