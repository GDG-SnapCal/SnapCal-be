package com.snapcal.snapcalbackend.service;

import com.snapcal.snapcalbackend.domain.*;
import com.snapcal.snapcalbackend.repository.CategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoCategoryRepository;
import com.snapcal.snapcalbackend.repository.PhotoRepository;
import com.snapcal.snapcalbackend.util.ImageAnalysisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoProcessingService {

    private final PhotoRepository photoRepository;
    private final PhotoCategoryRepository photoCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final ImageClassificationService classificationService;

    /**
     * GPT 분류 + pHash + 선명도 계산을 백그라운드 스레드에서 병렬 처리.
     * 각 사진이 완료될 때마다 PROCESSING → PENDING 전환.
     */
    @Async("photoProcessingExecutor")
    public void processAsync(Map<UUID, byte[]> photoIdToBytes, Map<UUID, String> photoIdToContentType) {
        for (Map.Entry<UUID, byte[]> entry : photoIdToBytes.entrySet()) {
            UUID photoId = entry.getKey();
            byte[] bytes = entry.getValue();
            String contentType = photoIdToContentType.getOrDefault(photoId, "image/jpeg");

            processOne(photoId, bytes, contentType);
        }
    }

    private void processOne(UUID photoId, byte[] bytes, String contentType) {
        try {
            Photo photo = photoRepository.findById(photoId).orElse(null);
            if (photo == null) return;

            // GPT 분류
            ImageClassificationService.ClassificationResult result =
                    classificationService.classify(bytes, contentType);
            Category category = categoryRepository.findByNameAndIsDefaultTrue(result.category())
                    .orElseGet(() -> categoryRepository.findByNameAndIsDefaultTrue("미분류").orElseThrow());

            // pHash + 선명도 계산
            Long phash = null;
            Double sharpness = null;
            try {
                phash = ImageAnalysisUtils.computePHash(bytes);
                sharpness = ImageAnalysisUtils.computeSharpness(bytes);
            } catch (Exception e) {
                log.warn("이미지 분석 실패 (pHash/선명도): {}", photoId, e);
            }

            photo.completeProcessing(phash, sharpness);
            photoRepository.save(photo);

            photoCategoryRepository.save(PhotoCategory.builder()
                    .photo(photo)
                    .category(category)
                    .classifiedBy(ClassifiedBy.AI)
                    .aiConfidence(result.confidence())
                    .userCorrected(false)
                    .build());

        } catch (Exception e) {
            log.error("사진 처리 중 오류 — 미분류로 fallback: photoId={}", photoId, e);
            fallbackToUnclassified(photoId);
        }
    }

    private void fallbackToUnclassified(UUID photoId) {
        try {
            Photo photo = photoRepository.findById(photoId).orElse(null);
            if (photo == null) return;

            Category unclassified = categoryRepository.findByNameAndIsDefaultTrue("미분류").orElse(null);
            if (unclassified == null) return;

            photo.completeProcessing(null, null);
            photoRepository.save(photo);

            photoCategoryRepository.save(PhotoCategory.builder()
                    .photo(photo)
                    .category(unclassified)
                    .classifiedBy(ClassifiedBy.AI)
                    .aiConfidence(0.0)
                    .userCorrected(false)
                    .build());
        } catch (Exception ex) {
            log.error("미분류 fallback 실패: photoId={}", photoId, ex);
        }
    }
}
