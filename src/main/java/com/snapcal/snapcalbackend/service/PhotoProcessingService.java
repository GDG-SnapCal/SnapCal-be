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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

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
            // 느린 작업(GPT, pHash)은 트랜잭션 밖에서 수행
            ImageClassificationService.ClassificationResult result =
                    classificationService.classify(bytes, contentType);

            Long phash = null;
            Double sharpness = null;
            try {
                phash = ImageAnalysisUtils.computePHash(bytes);
                sharpness = ImageAnalysisUtils.computeSharpness(bytes);
            } catch (Exception e) {
                log.warn("이미지 분석 실패 (pHash/선명도): {}", photoId, e);
            }

            final Long finalPhash = phash;
            final Double finalSharpness = sharpness;

            // photo 저장 + photo_category 저장을 하나의 트랜잭션으로 묶음
            new TransactionTemplate(transactionManager).execute(status -> {
                Photo photo = photoRepository.findById(photoId).orElse(null);
                if (photo == null) return null;

                Category category = categoryRepository.findByNameAndIsDefaultTrue(result.category())
                        .orElseGet(() -> categoryRepository.findByNameAndIsDefaultTrue("미분류").orElseThrow());

                photo.completeProcessing(finalPhash, finalSharpness);
                photoRepository.save(photo);

                photoCategoryRepository.save(PhotoCategory.builder()
                        .photo(photo)
                        .category(category)
                        .classifiedBy(ClassifiedBy.AI)
                        .aiConfidence(result.confidence())
                        .userCorrected(false)
                        .build());

                return null;
            });

        } catch (Exception e) {
            log.error("사진 처리 중 오류 — 미분류로 fallback: photoId={}", photoId, e);
            fallbackToUnclassified(photoId);
        }
    }

    private void fallbackToUnclassified(UUID photoId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                Photo photo = photoRepository.findById(photoId).orElse(null);
                if (photo == null) return null;

                Category unclassified = categoryRepository.findByNameAndIsDefaultTrue("미분류").orElse(null);
                if (unclassified == null) return null;

                photo.completeProcessing(null, null);
                photoRepository.save(photo);

                photoCategoryRepository.save(PhotoCategory.builder()
                        .photo(photo)
                        .category(unclassified)
                        .classifiedBy(ClassifiedBy.AI)
                        .aiConfidence(0.0)
                        .userCorrected(false)
                        .build());

                return null;
            });
        } catch (Exception ex) {
            log.error("미분류 fallback 실패: photoId={}", photoId, ex);
        }
    }
}
