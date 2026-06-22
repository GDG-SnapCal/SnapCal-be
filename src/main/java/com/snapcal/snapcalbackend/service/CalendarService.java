package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.Photo;
import com.snapcal.snapcalbackend.domain.PhotoCategory;
import com.snapcal.snapcalbackend.domain.PhotoStatus;
import com.snapcal.snapcalbackend.dto.request.CalendarSaveRequest;
import com.snapcal.snapcalbackend.dto.response.CalendarResponse;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /**
     * 업로드 배치(uploadId)에 속한 PENDING 사진들을 CONFIRMED로 전환하여 캘린더에 반영.
     * 중복이 없는 경우에도 동일하게 호출해야 함.
     */
    @Transactional
    public void saveToCalendar(UUID userId, CalendarSaveRequest request) {
        List<Photo> pending = photoRepository.findByUploadIdAndStatus(
                request.getUploadId(), PhotoStatus.PENDING);

        if (pending.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "저장 가능한 사진이 없습니다. uploadId를 확인하거나 이미 저장된 배치입니다.");
        }

        // 본인 소유 사진인지 검증 후 CONFIRMED 전환
        pending.stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .forEach(Photo::confirm);
    }

    @Transactional(readOnly = true)
    public CalendarResponse getMonthlyCalendar(UUID userId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        // CONFIRMED 상태 사진만 캘린더에 표시
        List<Photo> photos = photoRepository.findByUserIdAndTakenAtBetweenAndStatus(
                userId, start, end, PhotoStatus.CONFIRMED);

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
                .isRepresentative(photo.isRepresentative())
                .build();
    }
}
