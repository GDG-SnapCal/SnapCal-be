package com.snapcal.snapcalbackend.domain;

public enum PhotoStatus {
    /** AI 분류 진행 중 — 백그라운드 처리 완료 전 */
    PROCESSING,

    /** 분류 완료, 중복 검토 및 캘린더 저장 대기 중 */
    PENDING,

    /** 캘린더에 최종 저장된 사진 */
    CONFIRMED
}
