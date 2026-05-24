package com.snapcal.snapcalbackend.repository;

import com.snapcal.snapcalbackend.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    Optional<Category> findByNameAndIsDefaultTrue(String name);
    List<Category> findByUserIdOrIsDefaultTrue(UUID userId);
}
