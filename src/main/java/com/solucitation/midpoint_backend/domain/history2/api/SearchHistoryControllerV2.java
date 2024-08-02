package com.solucitation.midpoint_backend.domain.history2.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solucitation.midpoint_backend.domain.history2.dto.PlaceDtoV2;
import com.solucitation.midpoint_backend.domain.history2.dto.SearchHistoryRequestDtoV2;
import com.solucitation.midpoint_backend.domain.history2.dto.SearchHistoryResponseDtoV2;
import com.solucitation.midpoint_backend.domain.history2.service.SearchHistoryServiceV2;
import com.solucitation.midpoint_backend.domain.member.dto.ValidationErrorResponse;
import com.solucitation.midpoint_backend.domain.member.entity.Member;
import com.solucitation.midpoint_backend.domain.member.service.MemberService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search-history-v2")
public class SearchHistoryControllerV2 {
    private final MemberService memberService;
    private final Validator validator;
    private final SearchHistoryServiceV2 searchHistoryService;

    /**
     * 검색 기록을 저장합니다.
     * 
     * @param authentication 인증 정보
     * @param request 검색 기록 저장에 필요한 정보 (동 정보, 장소 리스트)
     * @return 장소를 성공적으로 저장하면 201 CREATED를 반환합니다.
     *         로그인을 하지 않고 시도 시 401 UNAUTHORIZED를 반환합니다.
     *         저장하려는 장소 정보에 문제가 있을 때 (필드가 공백이거나 null일 경우) 400 BAD_REQUEST를 반환합니다.
     *         기타 사유로 저장 실패 시 500 INTERNAL_SERVER_ERROR를 반환합니다.
     */
    @PostMapping("")
    public ResponseEntity<Object> saveHistory(Authentication authentication,
                                              @RequestBody SearchHistoryRequestDtoV2 request) {
        try {
            String neighborhood = request.getNeighborhood();
            List<PlaceDtoV2> searchHistoryRequestDtos = request.getHistoryDto();

            for (PlaceDtoV2 searchHistoryRequestDto : searchHistoryRequestDtos) {
                System.out.println("searchHistoryRequestDto = " + searchHistoryRequestDto.getPlaceId());
            }

            if (authentication == null || !authentication.isAuthenticated()) { // 인증 검증
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "해당 서비스를 이용하기 위해서는 로그인이 필요합니다."));
            }

            if (neighborhood == null || neighborhood.trim().isEmpty()) { // neighborhood 검증
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "EMPTY_FIELD", "message", "동 정보가 누락되었습니다."));
            }

            String memberEmail = authentication.getName();
            Member member = memberService.getMemberByEmail(memberEmail);

            for (PlaceDtoV2 place : searchHistoryRequestDtos) { // 리스트 내의 모든 요소에 대한 검증 수행
                Set<ConstraintViolation<PlaceDtoV2>> violations = validator.validate(place);
                if (!violations.isEmpty()) {
                    List<ValidationErrorResponse.FieldError> fieldErrors = violations.stream()
                            .map(violation -> new ValidationErrorResponse.FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                            .collect(Collectors.toList());
                    ValidationErrorResponse errorResponse = new ValidationErrorResponse(fieldErrors);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }
            }

            searchHistoryService.save(neighborhood, memberEmail, searchHistoryRequestDtos);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "장소를 저장하였습니다."));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "장소 저장 중 오류가 발생하였습니다."));
        }
    }


    /**
     * 사용자가 지금까지 저장한 검색 정보를 전부 최신순부터 가져옵니다.
     *
     * @param authentication 인증 정보
     * @return 검색 기록 조회가 성공하면 200 OK와 결과 리스트를 반환합니다.
     *         로그인을 하지 않고 시도 시 401 UNAUTHORIZED를 반환합니다.
     *         사용자를 찾을 수 없는 경우 404 NOT_FOUND를 반환합니다.
     *         기타 사유로 저장 실패 시 500 INTERNAL_SERVER_ERROR를 반환합니다.
     */
    @GetMapping("")
    public ResponseEntity<Object> getHistory(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "해당 서비스를 이용하기 위해서는 로그인이 필요합니다."));
            }

            String memberEmail = authentication.getName();
            Member member = memberService.getMemberByEmail(memberEmail);

            List<SearchHistoryResponseDtoV2> searchHistoryResponseDtos = searchHistoryService.getHistory(member);
            return ResponseEntity.ok(searchHistoryResponseDtos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "message", "검색 기록 조회 중 오류가 발생하였습니다."));
        }
    }

}