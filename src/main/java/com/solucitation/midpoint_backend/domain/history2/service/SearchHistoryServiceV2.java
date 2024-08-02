package com.solucitation.midpoint_backend.domain.history2.service;

import com.solucitation.midpoint_backend.domain.history2.dto.PlaceDtoV2;
import com.solucitation.midpoint_backend.domain.history2.dto.SearchHistoryRequestDtoV2;
import com.solucitation.midpoint_backend.domain.history2.dto.SearchHistoryResponseDtoV2;
import com.solucitation.midpoint_backend.domain.history2.entity.PlaceInfoV2;
import com.solucitation.midpoint_backend.domain.history2.entity.SearchHistoryV2;
import com.solucitation.midpoint_backend.domain.history2.repository.SearchHistoryRepositoryV2;
import com.solucitation.midpoint_backend.domain.member.entity.Member;
import com.solucitation.midpoint_backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class SearchHistoryServiceV2 {
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final String defaultImageUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, "ap-northeast-2", "place_default_image.png");
    private final SearchHistoryRepositoryV2 searchHistoryRepository;
    private final MemberService memberService;

    /**
     * 검색 기록을 저장합니다.
     * 
     * @param neighborhood 동 정보
     * @param memberEmail 사용자 정보
     * @param placeDtos 장소 정보 리스트
     */
    @Transactional
    public void save(String neighborhood, String memberEmail, List<PlaceDtoV2> placeDtos) {
        Member member = memberService.getMemberByEmail(memberEmail);

        SearchHistoryV2 searchHistory = SearchHistoryV2.builder()
                .member(member)
                .neighborhood(neighborhood)
                .build();

        for (PlaceDtoV2 dto : placeDtos) {
            String imageUrl = dto.getImageUrl();
            if (imageUrl == null || imageUrl.trim().isEmpty()) { // 이미지 url이 없거나 공백
                imageUrl = defaultImageUrl; // 해당 장소의 이미지가 존재하지 않을 경우 기본 이미지를 저장합니다.
            }
            
            PlaceInfoV2 placeInfo = PlaceInfoV2.builder()
                    .name(dto.getPlaceName())
                    .placeId(dto.getPlaceId())
                    .address(dto.getPlaceAddress())
                    .imageUrl(imageUrl)
                    .searchHistory(searchHistory) // 연관관계 설정
                    .build();
            searchHistory.getPlaceList().add(placeInfo);
        }
        searchHistoryRepository.save(searchHistory);
    }

    /**
     * 사용자가 저장한 검색 기록을 전부 최신순부터 반환합니다.
     * @param member 사용자
     * @return 검색 기록 정보 리스트
     */
    @Transactional(readOnly = true)
    public List<SearchHistoryResponseDtoV2> getHistory(Member member) {
        List<SearchHistoryV2> histories = searchHistoryRepository.findByMemberOrderBySearchDateDesc(member);

        return histories.stream()
                .map(history -> new SearchHistoryResponseDtoV2(
                        history.getNeighborhood(),
                        history.getSearchDate(),
                        history.getPlaceList().stream()
                                .map(placeInfo -> new PlaceDtoV2(
                                        placeInfo.getPlaceId(),
                                        placeInfo.getName(),
                                        placeInfo.getAddress(),
                                        placeInfo.getImageUrl()
                                ))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
    }
}