package com.snapcal.snapcalbackend.repository;

import com.snapcal.snapcalbackend.domain.PhotoCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoCategoryRepository extends JpaRepository<PhotoCategory, UUID> {
    Optional<PhotoCategory> findByPhotoId(UUID photoId);
    List<PhotoCategory> findByPhotoIdIn(List<UUID> photoIds);
    List<PhotoCategory> findAllByCategoryId(Integer categoryId);
    List<PhotoCategory> findByPhotoIdInAndIsCategoryRepresentativeTrue(List<UUID> photoIds);

    @Query("SELECT pc FROM PhotoCategory pc WHERE pc.photo.user.id = :userId AND pc.photo.takenAt = :date AND pc.category.id = :categoryId AND pc.isCategoryRepresentative = true")
    List<PhotoCategory> findCategoryRepresentatives(@Param("userId") UUID userId, @Param("date") LocalDate date, @Param("categoryId") Integer categoryId);
}
