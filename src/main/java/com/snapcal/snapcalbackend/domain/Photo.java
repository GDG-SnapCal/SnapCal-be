package com.snapcal.snapcalbackend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_url", nullable = false)
    private String originalUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "taken_at")
    private LocalDate takenAt;

    @Column(name = "exif_available", nullable = false)
    private boolean exifAvailable;

    @Column(name = "phash")
    private Long phash;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /** 업로드 배치 단위 식별자 — 같은 업로드 요청에서 생성된 사진끼리 동일한 값을 가짐 */
    @Column(name = "upload_id", nullable = false, length = 36)
    private String uploadId;

    /** PENDING: 중복 검토·캘린더 저장 대기 / CONFIRMED: 캘린더에 최종 저장 완료 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private PhotoStatus status = PhotoStatus.PENDING;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        if (status == null) {
            status = PhotoStatus.PENDING;
        }
    }

    public void confirm() {
        this.status = PhotoStatus.CONFIRMED;
    }
}
