package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.*;
import com.snapcal.snapcalbackend.dto.request.DuplicateSelectRequest;
import com.snapcal.snapcalbackend.dto.response.PhotoUploadResponse;
import com.snapcal.snapcalbackend.dto.response.UploadInitiatedResponse;
import com.snapcal.snapcalbackend.dto.response.UploadStatusResponse;
import com.snapcal.snapcalbackend.repository.CategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoRepository;
import com.snapcal.snapcalbackend.util.ExifExtractor;
import com.snapcal.snapcalbackend.util.ImageAnalysisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoUploadService {

    private final StorageService storageService;
    private final ExifExtractor exifExtractor;
    private final PhotoProcessingService photoProcessingService;
    private final PhotoRepository photoRepository;
    private final CategoryRepository categoryRepository;
    private final PhotoCategoryRepository photoCategoryRepository;

    /**
     * 스토리지 업로드 + PROCESSING 상태 저장까지만 동기 처리 후 즉시 202 반환.
     * AI 분류·pHash는 {@link PhotoProcessingService}가 백그라운드에서 처리.
     */
    public UploadInitiatedResponse upload(List<MultipartFile> files, User user) {
        String uploadId = UUID.randomUUID().toString();
        Map<UUID, byte[]> photoIdToBytes = new LinkedHashMap<>();
        Map<UUID, String> photoIdToContentType = new LinkedHashMap<>();

        for (MultipartFile file : files) {
            try {
                byte[] bytes = file.getBytes();

                String url = storageService.upload(bytes, user.getId().toString(),
                        file.getOriginalFilename(), file.getContentType());

                Optional<LocalDate> takenAt = exifExtractor.extract(bytes);

                // 각 save()는 Spring Data JPA의 @Transactional이 개별 커밋 보장
                Photo photo = photoRepository.save(Photo.builder()
                        .user(user)
                        .originalUrl(url)
                        .takenAt(takenAt.orElse(null))
                        .exifAvailable(takenAt.isPresent())
                        .uploadId(uploadId)
                        .status(PhotoStatus.PROCESSING)
                        .build());

                photoIdToBytes.put(photo.getId(), bytes);
                photoIdToContentType.put(photo.getId(), file.getContentType());

            } catch (Exception e) {
                log.warn("사진 업로드 실패: {}", file.getOriginalFilename(), e);
            }
        }

        // 저장된 Photo들이 커밋된 후 비동기 처리 시작
        if (!photoIdToBytes.isEmpty()) {
            photoProcessingService.processAsync(photoIdToBytes, photoIdToContentType);
        }

        return UploadInitiatedResponse.builder()
                .uploadId(uploadId)
                .total(photoIdToBytes.size())
                .build();
    }

    @Transactional(readOnly = true)
    public UploadStatusResponse getUploadStatus(String uploadId, User user) {
        List<Photo> photos = photoRepository.findByUploadId(uploadId);

        if (photos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "업로드 배치를 찾을 수 없습니다.");
        }

        boolean ownerMismatch = photos.stream()
                .anyMatch(p -> !p.getUser().getId().equals(user.getId()));
        if (ownerMismatch) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        int total = photos.size();
        int completed = (int) photos.stream()
                .filter(p -> p.getStatus() != PhotoStatus.PROCESSING)
                .count();

        if (completed < total) {
            return UploadStatusResponse.builder()
                    .uploadId(uploadId)
                    .status("processing")
                    .total(total)
                    .completed(completed)
                    .build();
        }

        PhotoUploadResponse result = buildResponseFromDb(uploadId, photos);
        return UploadStatusResponse.builder()
                .uploadId(uploadId)
                .status("done")
                .total(total)
                .completed(total)
                .duplicateGroups(result.getDuplicateGroups())
                .classifications(result.getClassifications())
                .build();
    }

    @Transactional
    public void delete(UUID photoId, User user) {
        Photo photo = photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));

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
    public void setRepresentative(UUID photoId, User user) {
        Photo photo = photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));

        if (photo.getTakenAt() != null) {
            photoRepository.findByUserIdAndTakenAtAndIsRepresentativeTrue(user.getId(), photo.getTakenAt())
                    .ifPresent(Photo::unsetRepresentative);
        }

        photo.setAsRepresentative();
    }

    @Transactional
    public void selectFromDuplicates(DuplicateSelectRequest request, User user) {
        for (DuplicateSelectRequest.Selection selection : request.getSelections()) {
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

    private PhotoUploadResponse buildResponseFromDb(String uploadId, List<Photo> photos) {
        List<UUID> photoIds = photos.stream().map(Photo::getId).toList();
        Map<UUID, PhotoCategory> categoryByPhotoId = photoCategoryRepository
                .findByPhotoIdIn(photoIds)
                .stream()
                .collect(Collectors.toMap(pc -> pc.getPhoto().getId(), pc -> pc));

        List<PhotoUploadResponse.Classification> classifications = photos.stream()
                .map(photo -> {
                    PhotoCategory pc = categoryByPhotoId.get(photo.getId());
                    String categoryName = (pc != null && pc.getCategory() != null)
                            ? pc.getCategory().getName() : "미분류";
                    return PhotoUploadResponse.Classification.builder()
                            .photoId(photo.getId().toString())
                            .url(photo.getOriginalUrl())
                            .takenAt(photo.getTakenAt() != null ? photo.getTakenAt().toString() : null)
                            .category(categoryName)
                            .build();
                }).toList();

        Map<LocalDate, List<Photo>> byDate = photos.stream()
                .filter(p -> p.getTakenAt() != null)
                .collect(Collectors.groupingBy(Photo::getTakenAt));

        List<PhotoUploadResponse.DuplicateGroup> duplicateGroups = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Photo>> entry : byDate.entrySet()) {
            List<Photo> dayPhotos = entry.getValue();
            if (dayPhotos.size() < 2) continue;

            List<List<Photo>> clusters = clusterPhotosByPHash(dayPhotos);
            for (List<Photo> cluster : clusters) {
                if (cluster.size() < 2) continue;

                Photo sharpest = cluster.stream()
                        .max(Comparator.comparingDouble(p -> p.getSharpness() != null ? p.getSharpness() : 0.0))
                        .orElse(cluster.get(0));

                duplicateGroups.add(PhotoUploadResponse.DuplicateGroup.builder()
                        .groupId(UUID.randomUUID().toString())
                        .takenAt(entry.getKey().toString())
                        .photos(cluster.stream()
                                .map(p -> PhotoUploadResponse.PhotoInfo.builder()
                                        .photoId(p.getId().toString())
                                        .url(p.getOriginalUrl())
                                        .takenAt(p.getTakenAt().toString())
                                        .build())
                                .toList())
                        .aiRecommendedPhotoId(sharpest.getId().toString())
                        .build());
            }
        }

        return PhotoUploadResponse.builder()
                .uploadId(uploadId)
                .status("done")
                .duplicateGroups(duplicateGroups.isEmpty() ? null : duplicateGroups)
                .classifications(classifications)
                .build();
    }

    private List<List<Photo>> clusterPhotosByPHash(List<Photo> photos) {
        List<List<Photo>> clusters = new ArrayList<>();
        boolean[] assigned = new boolean[photos.size()];

        for (int i = 0; i < photos.size(); i++) {
            if (assigned[i]) continue;
            List<Photo> cluster = new ArrayList<>();
            cluster.add(photos.get(i));
            assigned[i] = true;

            for (int j = i + 1; j < photos.size(); j++) {
                if (assigned[j]) continue;
                Long phashJ = photos.get(j).getPhash();
                if (phashJ == null) continue;

                boolean similar = cluster.stream()
                        .filter(m -> m.getPhash() != null)
                        .anyMatch(m -> ImageAnalysisUtils.isDuplicate(m.getPhash(), phashJ));
                if (similar) {
                    cluster.add(photos.get(j));
                    assigned[j] = true;
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }
}
