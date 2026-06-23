package com.snapcal.snapcalbackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhotoListResponse {
    private String photoId;
    private String thumbnailUrl;
    private String originalUrl;
    private String category;
    private boolean isRepresentative;
    private String takenAt;
}
