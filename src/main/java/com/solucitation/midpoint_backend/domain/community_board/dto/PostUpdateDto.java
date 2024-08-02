package com.solucitation.midpoint_backend.domain.community_board.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdateDto { // 게시글 수정 시 사용하는 DTO
    @Size(max=100, message="제목은 최대 100자까지 가능합니다.")
    private String title;

    private String content;

    private List<Long> postHashtag;

    private List<String> deleteImageUrl = new ArrayList<>();

    public void validate(int exist, int add) {
        if (postHashtag != null)  {
            if (postHashtag.size() != 2 || postHashtag.get(0).equals(postHashtag.get(1))) {
                throw new IllegalArgumentException("서로 다른 두 개의 해시태그를 선택해야 합니다.");
            }
        }

        if (title != null) {
            String trimmedTitle = title.trim();
            if (trimmedTitle.isEmpty()) {
                throw new IllegalArgumentException("제목은 비워둘 수 없습니다.");
            }
            if (trimmedTitle.length() > 100) {
                throw new IllegalArgumentException("제목은 최대 100자까지 가능합니다.");
            }
        }

        if (content != null) {
            String trimmedContent = content.trim();
            if (trimmedContent.isEmpty()) {
                throw new IllegalArgumentException("본문은 비워둘 수 없습니다.");
            }
        }

        if (deleteImageUrl != null) {
            if (exist - deleteImageUrl.size() + add  <= 0) { // 이미지를 올리지 않는 경우 처리
                throw new IllegalArgumentException("이미지는 최소 한 장 업로드해야 합니다.");
            }

            for (String s : deleteImageUrl) { // 삭제할 이미지 정보 1차 검증
                String trimmedDeleteImageUrl = s.trim();
                if (trimmedDeleteImageUrl.isEmpty()) {
                    throw new IllegalArgumentException("이미지가 유효하지 않습니다.");
                }
            }
        }
    }
}