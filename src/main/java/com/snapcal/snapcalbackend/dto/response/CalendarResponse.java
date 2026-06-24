package com.snapcal.snapcalbackend.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class CalendarResponse {

    private int year;
    private int month;
    private List<DayEntry> days;

    @Getter
    @Builder
    public static class DayEntry {
        private String date;
        private List<PhotoEntry> photos;
        private RepresentativePhotoInfo representativePhoto;
        private Map<String, RepresentativePhotoInfo> categoryRepresentatives;
    }

    @Getter
    @Builder
    public static class RepresentativePhotoInfo {
        private String photoId;
        private String thumbnailUrl;
    }

    @JsonIgnoreProperties({"representative"})
    @Getter
    @Builder
    public static class PhotoEntry {
        private String photoId;
        private String thumbnailUrl;
        private String category;
        private String categoryColor;
        @JsonProperty("isRepresentative")
        private boolean isRepresentative;
    }
}
