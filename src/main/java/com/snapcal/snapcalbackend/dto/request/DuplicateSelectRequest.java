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
        /** 중복 그룹 식별자 (업로드 응답의 groupId) */
        private String groupId;

        /** 유지할 사진 ID */
        private String selectedPhotoId;

        /**
         * 삭제할 사진 ID 목록 (그룹 내 선택되지 않은 사진들).
         * 프론트에서 그룹의 photos 중 selectedPhotoId를 제외한 나머지를 담아 전송.
         */
        private List<String> unselectedPhotoIds;
    }
}
