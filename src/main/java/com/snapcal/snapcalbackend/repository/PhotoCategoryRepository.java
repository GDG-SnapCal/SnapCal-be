package com.snapcal.snapcalbackend.repository;

import com.snapcal.snapcalbackend.domain.PhotoCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoCategoryRepository extends JpaRepository<PhotoCategory, UUID> {
    Optional<PhotoCategory> findByPhotoId(UUID photoId);
    List<PhotoCategory> findByPhotoIdIn(List<UUID> photoIds);
    List<PhotoCategory> findAllByCategoryId(Integer categoryId);
}
