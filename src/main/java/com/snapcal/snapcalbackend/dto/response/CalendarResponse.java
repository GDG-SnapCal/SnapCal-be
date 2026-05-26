package com.snapcal.snapcalbackend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

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
    }

    @Getter
    @Builder
    public static class PhotoEntry {
        private String photoId;
        private String thumbnailUrl;
        private String category;
        private String categoryColor;
    }
}
