package com.snapcal.snapcalbackend.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@JsonIgnoreProperties({"representative"})
@Getter
@Builder
public class PhotoListResponse {
    private String photoId;
    private String thumbnailUrl;
    private String originalUrl;
    private String category;
    @JsonProperty("isRepresentative")
    private boolean isRepresentative;
    private String takenAt;
}
