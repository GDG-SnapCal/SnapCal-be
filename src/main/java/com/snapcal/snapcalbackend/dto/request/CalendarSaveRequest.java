package com.snapcal.snapcalbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class CalendarSaveRequest {

    /** 업로드 시 발급된 배치 식별자 */
    @NotBlank
    private String uploadId;
}
