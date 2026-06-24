package com.snapcal.snapcalbackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhotoImageUpdateResponse {
    private String photoId;
    private String originalUrl;
    private String thumbnailUrl;
}
