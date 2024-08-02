package com.solucitation.midpoint_backend.domain.community_board.api;

import com.solucitation.midpoint_backend.domain.community_board.dto.PostResponseDto;
import com.solucitation.midpoint_backend.domain.community_board.service.PostService;
import com.solucitation.midpoint_backend.domain.member.entity.Member;
import com.solucitation.midpoint_backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts/search")
public class SearhController {
    private final PostService postService;
    private final MemberService memberService;

    /**
     * 게시글을 목적별로 검색합니다. 이때 목적이 여러 개일 경우 OR 조건으로 검색한 결과를 반환합니다.
     *
     * @param authentication 인증 정보
     * @param purposes 해시태그 ID 리스트
     * @return
     *          검색 성공 시 200 ok와 게시글 목록을 반환합니다.
     *          해시태그 관련 오류가 발생할 경우 400 BAD REQUEST 와 함께 에러 메시지를 반환합니다.
     *          기타 사유로 오류가 발생할 경우 500 INTERNAL_SERVER_ERROR 와 에러 메시지를 반환합니다.
     */
    @GetMapping("/purpose")
    public ResponseEntity<?> searchByPurpose(Authentication authentication, @RequestParam("purpose") List<Long> purposes) {
        try {
            Member member = null;

            if (!(authentication == null || !authentication.isAuthenticated())) {
                String memberEmail = authentication.getName();
                member = memberService.getMemberByEmail(memberEmail);
            }
            List<PostResponseDto> postResponseDto = postService.getPostByPurpose(member, purposes);
            return ResponseEntity.ok(postResponseDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "CONDITION_NOT_MET", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "게시글 검색 중 오류가 발생하였습니다."));
        }
    }

    /**
     * 게시글을 검색어로 검색합니다.
     * 검색어가 문장 형태일 경우 공백으로 파싱 후 각각의 단어에 대해 OR 조건으로 검색한 결과를 반환합니다.
     *
     * @param authentication 인증 정보
     * @param query 검색어
     * @return
     *          검색 성공 시 200 ok와 게시글 목록을 반환합니다.
     *          검색어를 입력하지 않거나 공백으로만 구성된 경우 400 BAD REQUEST 와 함께 에러 메시지를 반환합니다.
     *          기타 사유로 오류가 발생할 경우 500 INTERNAL_SERVER_ERROR 와 에러 메시지를 반환합니다.
     */
    @GetMapping("/query")
    public ResponseEntity<?> searchByQuery(Authentication authentication, @RequestParam("query") String query) {
        try {
            Member member = null;

            if (!(authentication == null || !authentication.isAuthenticated())) {
                String memberEmail = authentication.getName();
                member = memberService.getMemberByEmail(memberEmail);
            }
            List<PostResponseDto> postResponseDto = postService.getPostByQuery(member, query);
            return ResponseEntity.ok(postResponseDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "CONDITION_NOT_MET", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "게시글 검색 중 오류가 발생하였습니다."));
        }
    }
}