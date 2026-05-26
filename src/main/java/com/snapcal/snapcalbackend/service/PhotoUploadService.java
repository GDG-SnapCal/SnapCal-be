package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.*;
import com.snapcal.snapcalbackend.dto.request.DuplicateSelectRequest;
import com.snapcal.snapcalbackend.dto.response.PhotoUploadResponse;
import com.snapcal.snapcalbackend.repository.CategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoRepository;
import com.snapcal.snapcalbackend.util.ExifExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoUploadService {

    private final StorageService storageService;
    private final ExifExtractor exifExtractor;
    private final ImageClassificationService classificationService;
    private final PhotoRepository photoRepository;
    private final CategoryRepository categoryRepository;
    private final PhotoCategoryRepository photoCategoryRepository;

    @Transactional
    public PhotoUploadResponse upload(List<MultipartFile> files, User user) {
        List<UploadResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                byte[] bytes = file.getBytes();

                String url = storageService.upload(bytes, user.getId().toString(),
                        file.getOriginalFilename(), file.getContentType());

                Optional<LocalDate> takenAt = exifExtractor.extract(bytes);

                String categoryName = classificationService.classify(bytes, file.getContentType());
                Category category = categoryRepository.findByNameAndIsDefaultTrue(categoryName)
                        .orElseGet(() -> categoryRepository.findByNameAndIsDefaultTrue("미분류").orElseThrow());

                Photo photo = photoRepository.save(Photo.builder()
                        .user(user)
                        .originalUrl(url)
                        .takenAt(takenAt.orElse(null))
                        .exifAvailable(takenAt.isPresent())
                        .build());

                photoCategoryRepository.save(PhotoCategory.builder()
                        .photo(photo)
                        .category(category)
                        .classifiedBy(ClassifiedBy.AI)
                        .userCorrected(false)
                        .build());

                results.add(new UploadResult(photo, category));

            } catch (Exception e) {
                log.warn("사진 처리 실패: {}", file.getOriginalFilename(), e);
            }
        }

        return buildResponse(results);
    }

    @Transactional
    public void delete(UUID photoId, User user) {
        Photo photo = photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));

        storageService.delete(photo.getOriginalUrl());
        photoRepository.delete(photo);
    }

    @Transactional
    public void updateCategory(UUID photoId, Integer categoryId, User user) {
        Photo photo = photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NoSuchElementException("사진을 찾을 수 없습니다."));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NoSuchElementException("카테고리를 찾을 수 없습니다."));

        PhotoCategory photoCategory = photoCategoryRepository.findByPhotoId(photo.getId())
                .orElseThrow(() -> new NoSuchElementException("카테고리 정보를 찾을 수 없습니다."));

        photoCategory.updateCategory(category);
    }

    @Transactional
    public void selectFromDuplicates(DuplicateSelectRequest request, User user) {
        for (DuplicateSelectRequest.Selection selection : request.getSelections()) {
            UUID selectedId = UUID.fromString(selection.getSelectedPhotoId());

            // groupId에 속한 사진들 중 선택되지 않은 것 삭제
            // groupId = takenAt 기반 그룹이므로, 해당 날짜의 사진에서 미선택 항목 제거
            photoRepository.findById(selectedId).ifPresent(selected -> {
                if (selected.getTakenAt() == null) return;

                List<Photo> sameDay = photoRepository.findByUserIdAndTakenAtBetween(
                        user.getId(), selected.getTakenAt(), selected.getTakenAt());

                sameDay.stream()
                        .filter(p -> !p.getId().equals(selectedId))
                        .forEach(p -> {
                            storageService.delete(p.getOriginalUrl());
                            photoRepository.delete(p);
                        });
            });
        }
    }

    private PhotoUploadResponse buildResponse(List<UploadResult> results) {
        String uploadId = UUID.randomUUID().toString();

        // 날짜별 그룹핑 → 같은 날 2장 이상이면 중복 그룹
        Map<LocalDate, List<UploadResult>> byDate = results.stream()
                .filter(r -> r.photo().getTakenAt() != null)
                .collect(Collectors.groupingBy(r -> r.photo().getTakenAt()));

        List<PhotoUploadResponse.DuplicateGroup> duplicateGroups = byDate.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> {
                    List<UploadResult> group = e.getValue();
                    return PhotoUploadResponse.DuplicateGroup.builder()
                            .groupId(UUID.randomUUID().toString())
                            .takenAt(e.getKey().toString())
                            .photos(group.stream()
                                    .map(r -> PhotoUploadResponse.PhotoInfo.builder()
                                            .photoId(r.photo().getId().toString())
                                            .url(r.photo().getOriginalUrl())
                                            .takenAt(r.photo().getTakenAt().toString())
                                            .build())
                                    .toList())
                            .aiRecommendedPhotoId(group.get(0).photo().getId().toString())
                            .build();
                })
                .toList();

        List<PhotoUploadResponse.Classification> classifications = results.stream()
                .map(r -> PhotoUploadResponse.Classification.builder()
                        .photoId(r.photo().getId().toString())
                        .url(r.photo().getOriginalUrl())
                        .takenAt(r.photo().getTakenAt() != null ? r.photo().getTakenAt().toString() : null)
                        .category(r.category().getName())
                        .build())
                .toList();

        return PhotoUploadResponse.builder()
                .uploadId(uploadId)
                .status("done")
                .duplicateGroups(duplicateGroups.isEmpty() ? null : duplicateGroups)
                .classifications(classifications)
                .build();
    }

    private record UploadResult(Photo photo, Category category) {}
}
