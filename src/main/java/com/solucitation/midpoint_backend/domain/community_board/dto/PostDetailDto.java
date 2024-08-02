package com.solucitation.midpoint_backend.domain.community_board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailDto { // 게시글 상세 조회에 사용하는 DTO
    private String nickname;
    private String profileImagerUrl;
    private String title;
    private String content;
    private LocalDateTime createDate;
    private List<Long> postHashtags;
    private List<String> images;
    private int likeCnt;
    private Boolean likes = false;
}
