package com.solucitation.midpoint_backend.domain.community_board.repository;

import com.solucitation.midpoint_backend.domain.community_board.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
    Optional<Image> findByMemberIdAndPostIsNull(Long memberId); // 회원의 프로필 이미지를 찾는 경우

    // 게시글에서 해당 이미지가 포함된 개수를 반환합니다.
    @Query("SELECT COUNT(i) FROM Image i WHERE i.imageUrl = :imageUrl AND i.post.id = :postId")
    Long countByImageUrlAndPostId(@Param("imageUrl") String imageUrl, @Param("postId") Long postId);

    // 해당 게시글에서의 이미지 순서 번호를 반환합니다.
    @Query("SELECT i.order FROM Image i WHERE i.imageUrl = :imageUrl AND i.post.id = :postId")
    Integer findOrderByImageUrlAndPostId(@Param("imageUrl") String imageUrl, @Param("postId") Long postId);

    // 게시글 수정 시 삭제할 이미지 url을 이용하여 DB에서 삭제합니다.
    @Modifying
    @Query("DELETE FROM Image i WHERE i.imageUrl = :imageUrl AND i.post.id = :postId")
    void deleteImageByImageUrlAndPostId(@Param("imageUrl") String imageUrl, @Param("postId") Long postId);
}
