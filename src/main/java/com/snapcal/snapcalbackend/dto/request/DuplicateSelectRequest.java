package com.snapcal.snapcalbackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class DuplicateSelectRequest {

    @NotNull
    private String uploadId;

    @NotNull
    private List<Selection> selections;

    @Getter
    public static class Selection {
        private String groupId;
        private String selectedPhotoId;
    }
}
