package com.snapcal.snapcalbackend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadStatusResponse {
    private String uploadId;
    private String status;     // "processing" | "done"
    private int total;
    private int completed;

    // status == "done" 일 때만 포함
    private List<PhotoUploadResponse.DuplicateGroup> duplicateGroups;
    private List<PhotoUploadResponse.Classification> classifications;
}
