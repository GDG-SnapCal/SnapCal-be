package com.snapcal.snapcalbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CategoryCreateRequest {

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.")
    private String name;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상 코드 형식이 올바르지 않습니다. (예: #FAC775)")
    private String colorHex;
}
