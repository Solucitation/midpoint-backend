package com.solucitation.midpoint_backend.domain.community_board.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solucitation.midpoint_backend.domain.community_board.dto.*;
import com.solucitation.midpoint_backend.domain.community_board.service.PostService;

import com.solucitation.midpoint_backend.domain.member.dto.ValidationErrorResponse;
import com.solucitation.midpoint_backend.domain.member.entity.Member;
import com.solucitation.midpoint_backend.domain.member.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final MemberService memberService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * 모든 게시글을 요약된 형태로 생성일 최신순부터 가져옵니다.
     * 이때 로그인되어 있으며 해당 게시글에 좋아요를 눌렀을 경우에는 PostResponseDto 내 likes 필드를 true로 반환합니다. (기본값 false)
     *
     * @param authentication 인증정보
     * @return 성공 시 200 OK와 함께 게시글 목록을 반환하며, 실패 시 500 Internal Server Error를 반환합니다.
     */
    @GetMapping("")
    public ResponseEntity<?> getAllPosts(Authentication authentication) {
        try {
            Member member = null;

            if (!(authentication == null || !authentication.isAuthenticated())) {
                String memberEmail = authentication.getName();
                member = memberService.getMemberByEmail(memberEmail);
            }

            List<PostResponseDto> postResponseDtos = postService.getAllPosts(member);
            return ResponseEntity.ok(postResponseDtos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "게시글 조회 중 오류가 발생하였습니다."));
        }
    }

    /**
     * 특정 게시글 내용 전체를 가져옵니다.
     *
     * @param postId 게시글 번호
     * @return 성공 시 200 OK와 함께 게시글 상세 정보를 반환하며, 실패 시 404 Not Found 또는 500 Internal Server Error를 반환합니다.
     */
    @GetMapping("/{postId}")
    public ResponseEntity<?> getPost(@PathVariable Long postId, Authentication authentication) {
        try {
            Member read_member = null;

            if (!(authentication == null || !authentication.isAuthenticated())) {
                String memberEmail = authentication.getName();
                read_member = memberService.getMemberByEmail(memberEmail);
            }

            PostDetailDto postDetailDto = postService.getPostById(postId, read_member);
            return ResponseEntity.ok(postDetailDto);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "POST_NOT_FOUND", "message", "해당 게시글이 존재하지 않습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "게시글 조회 중 오류가 발생하였습니다."));
        }
    }

    /**
     * 로그인된 상태에서 게시글에 좋아요를 누르거나 취소합니다.
     *
     * @param postId 게시글 번호
     * @param authentication 인증정보
     * @return 좋아요 상태 변경에 성공하면 200 ok를 반환합니다.
     *         로그인을 하지 않고 시도 시 401 Unauthorized 에러를 반환합니다.
     *         사용자나 게시글을 찾을 수 없을 때는 404 Not Found 에러를 반환합니다.
     *         기타 이유로 상태 변경 실패 시 500 Internal Server Error를 반환합니다.
     */
    @PostMapping("/{postId}/likes")
    public ResponseEntity<?> changeLikes(@PathVariable Long postId, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "해당 서비스를 이용하기 위해서는 로그인이 필요합니다."));
            }

            postService.isPostExist(postId); // 게시글 존재 여부 확인
            String memberEmail = authentication.getName();
            Member member = memberService.getMemberByEmail(memberEmail);

            Boolean result = postService.changeLikes(memberEmail, postId);

            if (result) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of("message","좋아요를 눌렀습니다!"));
            } else {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of("message","좋아요를 취소하였습니다!"));
            }
        } catch (EntityNotFoundException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "POST_NOT_FOUND", "message", "해당 게시글이 존재하지 않습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "좋아요 상태를 변경하는 중 오류가 발생하였습니다."));
        }
    }

    /**
     *
     * @param authentication 인증정보
     * @param postRequestDtoJson 게시글 본문 내용
     * @param postImages 게시글 본문 이미지
     * @return 게시글 등록을 성공하면 201 CREATED를 반환합니다.
     *         로그인을 하지 않고 시도 시 401 Unauthorized 에러를 반환합니다.
     *         서로 다른 2개의 해시태그를 선택하지 않았을 시 400 BAD REQUEST 에러를 반환합니다.
     *         이미지를 업로드하지 않았거나 4장 이상 업로드 시도 시 400 BAD REQUEST 에러를 반환합니다.
     *         사용자를 찾을 수 없을 때는 404 Not Found 에러를 반환합니다.
     *         기타 이유로 업로드 실패 시 500 Internal Server Error를 반환합니다.
     */
    @PostMapping(value = "",  consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createPost(Authentication authentication,
                                        @RequestPart("postDto") String postRequestDtoJson,
                                        @RequestPart(value = "postImages", required = false) List<MultipartFile> postImages) throws JsonProcessingException {
        try {
            PostRequestDto postRequestDto =  objectMapper.readValue(postRequestDtoJson, PostRequestDto.class);
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "해당 서비스를 이용하기 위해서는 로그인이 필요합니다."));
            }

            String memberEmail = authentication.getName();
            Member member = memberService.getMemberByEmail(memberEmail);

            Set<ConstraintViolation<PostRequestDto>> violations = validator.validate(postRequestDto);
            if (!violations.isEmpty()) {
                List<ValidationErrorResponse.FieldError> fieldErrors = violations.stream()
                        .map(violation -> new ValidationErrorResponse.FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                        .collect(Collectors.toList());
                ValidationErrorResponse errorResponse = new ValidationErrorResponse(fieldErrors);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            if (postImages == null || postImages.isEmpty()) { // 이미지 필드 자체가 없는 경우
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "CONDITION_NOT_MET", "message", "이미지를 최소 1장 이상 업로드해야 합니다."));
            }

            if (postImages.size() > 3) { // 이미지가 있으면 최대 3장까지만 허용
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "CONDITION_NOT_MET", "message", "이미지는 최대 3장까지 업로드 가능합니다."));
            }

            postService.createPost(postRequestDto, member, postImages);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "게시글을 성공적으로 등록하였습니다."));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "게시글 등록 중 오류가 발생하였습니다."));
        }
    }

    /**
     * 내가 작성한 모든 게시글을 요약된 형태로 생성일 최신순부터 가져옵니다.
     *
     * @param authentication 인증 정보
     * @return  성공 시 200 OK와 함께 게시글 목록을 반환합니다.
     *          사용자를 찾을 수 없을 때는 404 Not Found 에러를 반환합니다.
     *          기타 이유로 조회 실패 시 500 Internal Server Error를 반환합니다.
     */
    @GetMapping("/mine")
    public ResponseEntity<?> getMyAllPosts(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "해당 서비스를 이용하기 위해서는 로그인이 필요합니다."));
            }

            String memberEmail = authentication.getName();
            Member member = memberService.getMemberByEmail(memberEmail);

            List<PostResponseDto> postResponseDto = postService.getMyAllPosts(member);
            return ResponseEntity.ok(postResponseDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "게시글 조회 중 오류가 발생하였습니다."));
        }
    }

    /**
     * 본인이 작성한 글을 삭제합니다.
     *
     * @param postId 게시글 번호
     * @param authentication 인증정보
     * @return 삭제 성공 시 204 No content 를 반환합니다.
     *         로그인을 하지 않고 시도 시 401 Unauthorized 에러를 반환합니다.
     *         사용자나 게시글을 찾을 수 없을 때는 404 Not Found 에러를 반환합니다.
     *         삭제하려는 글이 본인이 작성한 글이 아닐 경우 403 Forbidden 에러를 반환힙니다.
     *         기타 이유로 게시글 삭제 실패 시 500 Internal Server Error를 반환합니다.
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable Long postId, Authentication authentication){
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "해당 서비스를 이용하기 위해서는 로그인이 필요합니다."));
            }

            postService.isPostExist(postId); // 게시글 존재 여부 확인

            String memberEmail = authentication.getName();
            Member member = memberService.getMemberByEmail(memberEmail);

            postService.deletePost(member, postId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .body(Map.of("message", "게시글을 성공적으로 삭제하였습니다."));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "POST_NOT_FOUND", "message", "해당 게시글이 존재하지 않습니다."));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "게시글 삭제 중 오류가 발생하였습니다."));
        }
    }

    @PatchMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updatePost(@PathVariable Long postId,
                                        Authentication authentication,
                                        @RequestPart("postDto") String postUpdateDtoJson,
                                        @RequestPart(value = "postImages", required = false) List<MultipartFile> postImages) throws JsonProcessingException {
        try{
            PostUpdateDto postUpdateDto =  objectMapper.readValue(postUpdateDtoJson, PostUpdateDto.class);

            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "해당 서비스를 이용하기 위해서는 로그인이 필요합니다."));
            }

            String memberEmail = authentication.getName();
            Member member = memberService.getMemberByEmail(memberEmail);

            int nowImageCnt = postService.getPostById(postId, member).getImages().size();
            int validImageCnt = 0;

            if (postImages != null && !postImages.isEmpty())  { // 이미지 변경이 있는 경우
                int nextImageCnt = nowImageCnt - postUpdateDto.getDeleteImageUrl().size(); // 삭제 작업만 진행했을 때의 이미지 개수

                for (MultipartFile postImage : postImages) { // 추가할 이미지 개수
                    if (postImage != null && !postImage.isEmpty()) validImageCnt++;
                }
                if (nextImageCnt + validImageCnt > 3) // 최종 이미지 개수
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "CONDITION_NOT_MET", "message", "이미지는 최대 3장까지 업로드 가능합니다."));
            }
            postUpdateDto.validate(nowImageCnt, validImageCnt); // 제목, 본문, 해시태그, 삭제할 이미지 검증
            postService.updatePost(postId, postUpdateDto, member, postImages);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(Map.of("message", "게시글을 성공적으로 수정했습니다."));
        }  catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "POST_NOT_FOUND", "message", "해당 게시글이 존재하지 않습니다."));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "CONDITION_NOT_MET", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "게시글 수정 중 오류가 발생하였습니다."));
        }
    }
}