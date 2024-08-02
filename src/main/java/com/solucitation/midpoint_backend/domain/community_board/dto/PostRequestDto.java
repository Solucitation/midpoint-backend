package com.solucitation.midpoint_backend.domain.community_board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostRequestDto { // 게시글 작성 시 사용하는 DTO
    private Long memberId;

    @NotBlank(message = "제목은 비워둘 수 없습니다.")
    @Size(max=100, message="제목은 최대 100자까지 가능합니다.")
    private String title;

    @NotBlank(message = "본문은 비워둘 수 없습니다.")
    private String content;

    private List<Long> postHashtag;

    public void validatePostHashtags() {
        if (postHashtag == null || postHashtag.size() != 2 || postHashtag.get(0).equals(postHashtag.get(1))) {
            throw new IllegalArgumentException("서로 다른 두 개의 해시태그를 선택해야 합니다.");
        }
    }
}