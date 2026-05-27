package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.*;
import com.snapcal.snapcalbackend.dto.request.DuplicateSelectRequest;
import com.snapcal.snapcalbackend.dto.response.PhotoUploadResponse;
import com.snapcal.snapcalbackend.repository.CategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoRepository;
import com.snapcal.snapcalbackend.util.ExifExtractor;
import com.snapcal.snapcalbackend.util.ImageAnalysisUtils;
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

        // 같은 업로드 요청 묶음을 식별하는 배치 ID
        String uploadId = UUID.randomUUID().toString();

        for (MultipartFile file : files) {
            try {
                byte[] bytes = file.getBytes();

                // 스토리지 업로드
                String url = storageService.upload(bytes, user.getId().toString(),
                        file.getOriginalFilename(), file.getContentType());

                // EXIF 촬영일 추출
                Optional<LocalDate> takenAt = exifExtractor.extract(bytes);

                // GPT 카테고리 분류
                String categoryName = classificationService.classify(bytes, file.getContentType());
                Category category = categoryRepository.findByNameAndIsDefaultTrue(categoryName)
                        .orElseGet(() -> categoryRepository.findByNameAndIsDefaultTrue("미분류").orElseThrow());

                // pHash + 선명도 계산 (실패해도 업로드는 계속)
                Long phash = null;
                double sharpness = 0.0;
                try {
                    phash = ImageAnalysisUtils.computePHash(bytes);
                    sharpness = ImageAnalysisUtils.computeSharpness(bytes);
                } catch (Exception e) {
                    log.warn("이미지 분석 실패 (pHash/선명도): {}", file.getOriginalFilename(), e);
                }

                // PENDING 상태로 저장 — 캘린더 반영은 POST /api/calendar/save 호출 시 확정
                Photo photo = photoRepository.save(Photo.builder()
                        .user(user)
                        .originalUrl(url)
                        .takenAt(takenAt.orElse(null))
                        .exifAvailable(takenAt.isPresent())
                        .phash(phash)
                        .uploadId(uploadId)
                        .status(com.snapcal.snapcalbackend.domain.PhotoStatus.PENDING)
                        .build());

                photoCategoryRepository.save(PhotoCategory.builder()
                        .photo(photo)
                        .category(category)
                        .classifiedBy(ClassifiedBy.AI)
                        .userCorrected(false)
                        .build());

                results.add(new UploadResult(photo, category, phash, sharpness));

            } catch (Exception e) {
                log.warn("사진 처리 실패: {}", file.getOriginalFilename(), e);
            }
        }

        return buildResponse(uploadId, results);
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
            // 요청에서 명시적으로 받은 삭제 대상만 처리 (같은 날짜 전체 삭제 버그 제거)
            for (String unselectedId : selection.getUnselectedPhotoIds()) {
                UUID photoId = UUID.fromString(unselectedId);

                photoRepository.findById(photoId)
                        .filter(p -> p.getUser().getId().equals(user.getId()))
                        .ifPresent(p -> {
                            storageService.delete(p.getOriginalUrl());
                            photoRepository.delete(p);
                        });
            }
        }
    }

    // ── private ──────────────────────────────────────────────────────

    private PhotoUploadResponse buildResponse(String uploadId, List<UploadResult> results) {

        // 날짜별 그룹핑 (EXIF 없는 사진 제외)
        Map<LocalDate, List<UploadResult>> byDate = results.stream()
                .filter(r -> r.photo().getTakenAt() != null)
                .collect(Collectors.groupingBy(r -> r.photo().getTakenAt()));

        List<PhotoUploadResponse.DuplicateGroup> duplicateGroups = new ArrayList<>();

        for (Map.Entry<LocalDate, List<UploadResult>> entry : byDate.entrySet()) {
            List<UploadResult> dayPhotos = entry.getValue();
            if (dayPhotos.size() < 2) continue;

            // pHash 기반 유사도 클러스터링
            List<List<UploadResult>> clusters = clusterByPHash(dayPhotos);

            for (List<UploadResult> cluster : clusters) {
                if (cluster.size() < 2) continue;

                // 가장 선명한 사진을 AI 추천 대표 사진으로 선택
                UploadResult sharpest = cluster.stream()
                        .max(Comparator.comparingDouble(UploadResult::sharpness))
                        .orElse(cluster.get(0));

                duplicateGroups.add(PhotoUploadResponse.DuplicateGroup.builder()
                        .groupId(UUID.randomUUID().toString())
                        .takenAt(entry.getKey().toString())
                        .photos(cluster.stream()
                                .map(r -> PhotoUploadResponse.PhotoInfo.builder()
                                        .photoId(r.photo().getId().toString())
                                        .url(r.photo().getOriginalUrl())
                                        .takenAt(r.photo().getTakenAt().toString())
                                        .build())
                                .toList())
                        .aiRecommendedPhotoId(sharpest.photo().getId().toString())
                        .build());
            }
        }

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

    /**
     * 같은 날 사진들을 pHash 유사도 기준으로 클러스터링
     * 한 클러스터 안에 있는 사진끼리는 서로 시각적으로 유사 (버스트샷 등)
     */
    private List<List<UploadResult>> clusterByPHash(List<UploadResult> photos) {
        List<List<UploadResult>> clusters = new ArrayList<>();
        boolean[] assigned = new boolean[photos.size()];

        for (int i = 0; i < photos.size(); i++) {
            if (assigned[i]) continue;

            List<UploadResult> cluster = new ArrayList<>();
            cluster.add(photos.get(i));
            assigned[i] = true;

            for (int j = i + 1; j < photos.size(); j++) {
                if (assigned[j]) continue;

                // pHash가 null이면 중복으로 판단하지 않음
                Long phashJ = photos.get(j).phash();
                if (phashJ == null) continue;

                boolean similarToCluster = cluster.stream()
                        .filter(m -> m.phash() != null)
                        .anyMatch(m -> ImageAnalysisUtils.isDuplicate(m.phash(), phashJ));

                if (similarToCluster) {
                    cluster.add(photos.get(j));
                    assigned[j] = true;
                }
            }

            clusters.add(cluster);
        }

        return clusters;
    }

    private record UploadResult(Photo photo, Category category, Long phash, double sharpness) {}
}
