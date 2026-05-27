package com.snapcal.snapcalbackend.dto.response;

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
    private Map<String, DateEntry> dates;

    @Getter
    @Builder
    public static class DayEntry {
        private String date;
        private List<PhotoEntry> photos;
    }

    @Getter
    @Builder
    public static class PhotoEntry {
        private String photoId;
        private String thumbnailUrl;
        private String category;
        private String categoryColor;
    }

    @Getter
    @Builder
    public static class DateEntry {
        private int count;
        private RepresentativePhoto representativePhoto;
    }

    @Getter
    @Builder
    public static class RepresentativePhoto {
        private String photoId;
        private String thumbnailUrl;
    }
}
