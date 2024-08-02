package com.solucitation.midpoint_backend.domain.history2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryRequestDtoV2 { // 검색 기록 저장 시 사용하는 DTO
    @NotBlank
    private String neighborhood;

    private List<PlaceDtoV2> historyDto = new ArrayList<>();
}