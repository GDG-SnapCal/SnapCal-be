package com.snapcal.snapcalbackend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UploadStatusResponse {
    private String status;
    private List<PhotoUploadResponse.DuplicateGroup> duplicateGroups;
    private List<PhotoUploadResponse.Classification> classifications;
    private String error;
}
