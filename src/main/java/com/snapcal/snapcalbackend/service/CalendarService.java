package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.Photo;
import com.snapcal.snapcalbackend.domain.PhotoCategory;
import com.snapcal.snapcalbackend.dto.response.CalendarResponse;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final PhotoRepository photoRepository;
    private final PhotoCategoryRepository photoCategoryRepository;

    @Transactional(readOnly = true)
    public CalendarResponse getMonthlyCalendar(UUID userId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Photo> photos = photoRepository.findByUserIdAndTakenAtBetween(userId, start, end);

        if (photos.isEmpty()) {
            return CalendarResponse.builder()
                    .year(year)
                    .month(month)
                    .days(List.of())
                    .build();
        }

        List<UUID> photoIds = photos.stream().map(Photo::getId).toList();
        Map<UUID, PhotoCategory> categoryByPhotoId = photoCategoryRepository
                .findByPhotoIdIn(photoIds)
                .stream()
                .collect(Collectors.toMap(pc -> pc.getPhoto().getId(), pc -> pc));

        Map<LocalDate, List<Photo>> byDate = photos.stream()
                .collect(Collectors.groupingBy(Photo::getTakenAt));

        List<CalendarResponse.DayEntry> days = byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> CalendarResponse.DayEntry.builder()
                        .date(entry.getKey().toString())
                        .photos(entry.getValue().stream()
                                .map(photo -> toPhotoEntry(photo, categoryByPhotoId.get(photo.getId())))
                                .toList())
                        .build())
                .toList();

        return CalendarResponse.builder()
                .year(year)
                .month(month)
                .days(days)
                .build();
    }

    private CalendarResponse.PhotoEntry toPhotoEntry(Photo photo, PhotoCategory photoCategory) {
        String url = photo.getThumbnailUrl() != null ? photo.getThumbnailUrl() : photo.getOriginalUrl();

        String categoryName = "미분류";
        String categoryColor = "#E8E8E8";
        if (photoCategory != null && photoCategory.getCategory() != null) {
            categoryName = photoCategory.getCategory().getName();
            categoryColor = photoCategory.getCategory().getColorHex();
        }

        return CalendarResponse.PhotoEntry.builder()
                .photoId(photo.getId().toString())
                .thumbnailUrl(url)
                .category(categoryName)
                .categoryColor(categoryColor)
                .build();
    }
}
