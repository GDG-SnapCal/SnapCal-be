package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.Category;
import com.snapcal.snapcalbackend.domain.ClassifiedBy;
import com.snapcal.snapcalbackend.domain.Photo;
import com.snapcal.snapcalbackend.domain.PhotoCategory;
import com.snapcal.snapcalbackend.domain.PhotoStatus;
import com.snapcal.snapcalbackend.domain.User;
import com.snapcal.snapcalbackend.dto.request.DuplicateSelectRequest;
import com.snapcal.snapcalbackend.dto.response.PhotoUploadResponse;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
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
        String uploadId = UUID.randomUUID().toString();

        for (MultipartFile file : files) {
            try {
                byte[] bytes = file.getBytes();

                String url = storageService.upload(bytes, user.getId().toString(),
                        file.getOriginalFilename(), file.getContentType());

                Optional<LocalDate> takenAt = exifExtractor.extract(bytes);

                String categoryName = classificationService.classify(bytes, file.getContentType());
                Category category = categoryRepository.findByNameAndIsDefaultTrue(categoryName)
                        .orElseGet(() -> categoryRepository.findByNameAndIsDefaultTrue("미분류").orElseThrow());

                Long phash = null;
                double sharpness = 0.0;
                try {
                    phash = ImageAnalysisUtils.computePHash(bytes);
                    sharpness = ImageAnalysisUtils.computeSharpness(bytes);
                } catch (Exception e) {
                    log.warn("Image analysis failed for {}", file.getOriginalFilename(), e);
                }

                Photo photo = photoRepository.save(Photo.builder()
                        .user(user)
                        .originalUrl(url)
                        .takenAt(takenAt.orElse(null))
                        .exifAvailable(takenAt.isPresent())
                        .phash(phash)
                        .uploadId(uploadId)
                        .status(PhotoStatus.PENDING)
                        .build());

                photoCategoryRepository.save(PhotoCategory.builder()
                        .photo(photo)
                        .category(category)
                        .classifiedBy(ClassifiedBy.AI)
                        .userCorrected(false)
                        .build());

                results.add(new UploadResult(photo, category, phash, sharpness));
            } catch (Exception e) {
                log.warn("Photo processing failed for {}", file.getOriginalFilename(), e);
            }
        }

        return buildResponse(uploadId, results);
    }

    @Transactional
    public void delete(UUID photoId, User user) {
        Photo photo = photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."));

        storageService.delete(photo.getOriginalUrl());
        photoRepository.delete(photo);
    }

    @Transactional
    public void updateCategory(UUID photoId, Integer categoryId, User user) {
        Photo photo = photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NoSuchElementException("Photo not found."));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NoSuchElementException("Category not found."));

        PhotoCategory photoCategory = photoCategoryRepository.findByPhotoId(photo.getId())
                .orElseThrow(() -> new NoSuchElementException("Photo category not found."));

        photoCategory.updateCategory(category);
    }

    @Transactional
    public void selectFromDuplicates(DuplicateSelectRequest request, User user) {
        for (DuplicateSelectRequest.Selection selection : request.getSelections()) {
            if (selection.getUnselectedPhotoIds() == null || selection.getUnselectedPhotoIds().isEmpty()) {
                continue;
            }

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

    @Transactional(readOnly = true)
    public UploadStatusResponse getUploadStatus(String uploadId, User user) {
        List<Photo> photos = photoRepository.findByUploadIdAndUserId(uploadId, user.getId());
        if (photos.isEmpty()) {
            return UploadStatusResponse.builder()
                    .status("processing")
                    .build();
        }

        List<PhotoUploadResponse.Classification> classifications = photos.stream()
                .map(photo -> {
                    String categoryName = photoCategoryRepository.findByPhotoId(photo.getId())
                            .map(pc -> pc.getCategory().getName())
                            .orElse("미분류");

                    return PhotoUploadResponse.Classification.builder()
                            .photoId(photo.getId().toString())
                            .url(photo.getOriginalUrl())
                            .takenAt(photo.getTakenAt() != null ? photo.getTakenAt().toString() : null)
                            .category(categoryName)
                            .build();
                })
                .toList();

        return UploadStatusResponse.builder()
                .status("done")
                .classifications(classifications)
                .duplicateGroups(null)
                .build();
    }

    @Transactional(readOnly = true)
    public String getEditablePhotoUrl(UUID photoId, User user) {
        return photoRepository.findById(photoId)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .map(Photo::getOriginalUrl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."));
    }

    private PhotoUploadResponse buildResponse(String uploadId, List<UploadResult> results) {
        Map<LocalDate, List<UploadResult>> byDate = results.stream()
                .filter(r -> r.photo().getTakenAt() != null)
                .collect(Collectors.groupingBy(r -> r.photo().getTakenAt()));

        List<PhotoUploadResponse.DuplicateGroup> duplicateGroups = new ArrayList<>();

        for (Map.Entry<LocalDate, List<UploadResult>> entry : byDate.entrySet()) {
            List<UploadResult> dayPhotos = entry.getValue();
            if (dayPhotos.size() < 2) {
                continue;
            }

            List<List<UploadResult>> clusters = clusterByPHash(dayPhotos);

            for (List<UploadResult> cluster : clusters) {
                if (cluster.size() < 2) {
                    continue;
                }

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

    private List<List<UploadResult>> clusterByPHash(List<UploadResult> photos) {
        List<List<UploadResult>> clusters = new ArrayList<>();
        boolean[] assigned = new boolean[photos.size()];

        for (int i = 0; i < photos.size(); i++) {
            if (assigned[i]) {
                continue;
            }

            List<UploadResult> cluster = new ArrayList<>();
            cluster.add(photos.get(i));
            assigned[i] = true;

            for (int j = i + 1; j < photos.size(); j++) {
                if (assigned[j]) {
                    continue;
                }

                Long phashJ = photos.get(j).phash();
                if (phashJ == null) {
                    continue;
                }

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
