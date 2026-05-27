package com.snapcal.snapcalbackend.repository;

import com.snapcal.snapcalbackend.domain.Photo;
import com.snapcal.snapcalbackend.domain.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    List<Photo> findByUserIdAndTakenAtBetweenAndStatus(
            UUID userId, LocalDate start, LocalDate end, PhotoStatus status);

    List<Photo> findByUploadIdAndStatus(String uploadId, PhotoStatus status);

    List<Photo> findByUploadIdAndUserId(String uploadId, UUID userId);

    List<Photo> findByUserIdAndTakenAtBetween(UUID userId, LocalDate start, LocalDate end);
}
