package com.snapcal.snapcalbackend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.snapcal.snapcalbackend.domain.Photo;
import com.snapcal.snapcalbackend.domain.PhotoCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhotoDetailResponse {

    private String photoId;
    private String url;
    private String thumbnailUrl;
    private String takenAt;
    private boolean exifAvailable;
    private String uploadedAt;
    private CategoryInfo category;

    @Getter
    @Builder
    public static class CategoryInfo {
        private Integer categoryId;
        private String name;
        private String colorHex;
        private String classifiedBy;
    }

    public static PhotoDetailResponse of(Photo photo, PhotoCategory photoCategory) {
        CategoryInfo categoryInfo = null;
        if (photoCategory != null && photoCategory.getCategory() != null) {
            categoryInfo = CategoryInfo.builder()
                    .categoryId(photoCategory.getCategory().getId())
                    .name(photoCategory.getCategory().getName())
                    .colorHex(photoCategory.getCategory().getColorHex())
                    .classifiedBy(photoCategory.getClassifiedBy().name())
                    .build();
        }

        return PhotoDetailResponse.builder()
                .photoId(photo.getId().toString())
                .url(photo.getOriginalUrl())
                .thumbnailUrl(photo.getThumbnailUrl())
                .takenAt(photo.getTakenAt() != null ? photo.getTakenAt().toString() : null)
                .exifAvailable(photo.isExifAvailable())
                .uploadedAt(photo.getUploadedAt().toString())
                .category(categoryInfo)
                .build();
    }
}
