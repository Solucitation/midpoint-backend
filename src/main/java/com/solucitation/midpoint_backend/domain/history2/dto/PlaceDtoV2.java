package com.solucitation.midpoint_backend.domain.history2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDtoV2 { // 장소 정보 DTO
    @NotBlank(message = "장소ID가 누락되었습니다.")
    private String placeId;  // 장소의 고유 ID

    @NotBlank(message = "장소명이 누락되었습니다.")
    private String placeName; // 장소의 이름

    @NotBlank(message = "주소가 누락되었습니다.")
    private String placeAddress; // 장소의 주소

    private String imageUrl; // 장소 이미지
}