package com.snapcal.snapcalbackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadInitiatedResponse {
    private String uploadId;
    private int total;
}
