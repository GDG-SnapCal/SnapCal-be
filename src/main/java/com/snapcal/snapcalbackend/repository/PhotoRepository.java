package com.snapcal.snapcalbackend.repository;

import com.snapcal.snapcalbackend.domain.Photo;
import com.snapcal.snapcalbackend.domain.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    /** 캘린더 월별 조회 — CONFIRMED 상태만 */
    List<Photo> findByUserIdAndTakenAtBetweenAndStatus(
            UUID userId, LocalDate start, LocalDate end, PhotoStatus status);

    /** 업로드 배치 전체 조회 — 캘린더 저장 시 사용 */
    List<Photo> findByUploadIdAndStatus(String uploadId, PhotoStatus status);

    /** 특정 날짜의 현재 대표 사진 조회 — 대표 사진 교체 시 기존 해제용 */
    Optional<Photo> findByUserIdAndTakenAtAndIsRepresentativeTrue(UUID userId, LocalDate takenAt);

    /** 레거시 호환 — 내부적으로 사용하지 않도록 점진적 제거 예정 */
    List<Photo> findByUserIdAndTakenAtBetween(UUID userId, LocalDate start, LocalDate end);
}
