package com.snapcal.snapcalbackend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhotoUploadResponse {

    private String uploadId;
    private String status;           // "done" | "processing" | "error"
    private List<DuplicateGroup> duplicateGroups;
    private List<Classification> classifications;

    @Getter
    @Builder
    public static class DuplicateGroup {
        private String groupId;
        private String takenAt;
        private List<PhotoInfo> photos;
        private String aiRecommendedPhotoId;
    }

    @Getter
    @Builder
    public static class PhotoInfo {
        private String photoId;
        private String url;
        private String takenAt;
    }

    @Getter
    @Builder
    public static class Classification {
        private String photoId;
        private String url;
        private String takenAt;
        private String category;
    }
}
