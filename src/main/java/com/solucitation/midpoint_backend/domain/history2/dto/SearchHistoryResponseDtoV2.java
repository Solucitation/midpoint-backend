package com.solucitation.midpoint_backend.domain.history2.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryResponseDtoV2 { // 검색 기록 리스트 시 사용하는 DTO
    private String neighborhood;
    private LocalDateTime searchTime;
    private List<PlaceDtoV2> places = new ArrayList<>();
}