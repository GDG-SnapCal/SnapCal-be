package com.snapcal.snapcalbackend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.snapcal.snapcalbackend.domain.Category;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryResponse {

    private Integer categoryId;
    private String name;
    private String colorHex;
    @JsonProperty("isDefault")
    private boolean defaultCategory;  // 필드명 isDefault → defaultCategory (Lombok getter 충돌 방지)

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getId())
                .name(category.getName())
                .colorHex(category.getColorHex())
                .defaultCategory(category.isDefault())
                .build();
    }
}
