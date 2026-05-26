package com.snapcal.snapcalbackend.dto.response;

import com.snapcal.snapcalbackend.domain.Category;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryResponse {

    private Integer categoryId;
    private String name;
    private String colorHex;
    private boolean isDefault;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getId())
                .name(category.getName())
                .colorHex(category.getColorHex())
                .isDefault(category.isDefault())
                .build();
    }
}
