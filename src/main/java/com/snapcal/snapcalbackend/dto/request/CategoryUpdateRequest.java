package com.snapcal.snapcalbackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CategoryUpdateRequest {

    @NotNull(message = "categoryId를 입력해주세요.")
    private Integer categoryId;
}
