package com.snapcal.snapcalbackend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "photo_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PhotoCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "classified_by", nullable = false, length = 10)
    private ClassifiedBy classifiedBy;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "user_corrected", nullable = false)
    private boolean userCorrected;

    @Column(name = "classified_at", nullable = false, updatable = false)
    private LocalDateTime classifiedAt;

    @PrePersist
    protected void onCreate() {
        classifiedAt = LocalDateTime.now();
    }

    public void updateCategory(Category newCategory) {
        this.category = newCategory;
        this.classifiedBy = ClassifiedBy.USER;
        this.userCorrected = true;
    }

    /** 카테고리 삭제 시 미분류로 이동 — 분류 주체/정정 여부는 그대로 유지 */
    public void migrateToDefault(Category defaultCategory) {
        this.category = defaultCategory;
    }
}
