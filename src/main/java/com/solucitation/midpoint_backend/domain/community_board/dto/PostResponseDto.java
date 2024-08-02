package com.solucitation.midpoint_backend.domain.community_board.dto;

import com.solucitation.midpoint_backend.domain.community_board.entity.Image;
import com.solucitation.midpoint_backend.domain.community_board.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class PostResponseDto { // 게시글 리스트 상태에서 보이는 게시글 정보 DTO
    private Long postId;
    private String firstImageUrl;
    private String title;
    private List<Long> hashtags;
    private Boolean likes;

    public PostResponseDto(Post post) {
        this.postId = post.getId();
        this.title = post.getTitle();
        this.likes = false;

        if (!post.getImages().isEmpty()) {
            // 이미지 리스트를 order 값에 따라 오름차순으로 정렬
            List<Image> sortedImages = post.getImages().stream()
                    .sorted(Comparator.comparingInt(image -> {
                        Integer order = image.getOrder();
                        return (order != null) ? order : Integer.MAX_VALUE; // order가 null인 이미지는 마지막으로 이동
                    }))
                    .collect(Collectors.toList());

            // order 값이 1인 이미지를 찾기
            Image targetImage = sortedImages.stream()
                    .filter(image -> image.getOrder() != null && image.getOrder() == 1)
                    .findFirst()
                    .orElse(null);


            // 첫 번째 이미지의 URL을 설정
            this.firstImageUrl = sortedImages.get(0).getImageUrl();
        }

        this.hashtags = post.getPostHashtags().stream()
                .map(postHashtag -> postHashtag.getHashtag().getId())
                .collect(Collectors.toList());

    }
}