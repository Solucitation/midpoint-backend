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
                imageUrl = defaultImageUrl;
            }
            
            PlaceInfoV2 placeInfo = PlaceInfoV2.builder()
                    .name(dto.getPlaceName())
                    .placeId(dto.getPlaceId())
                    .address(dto.getPlaceAddress())
                    .imageUrl(imageUrl)
                    .searchHistory(searchHistory) // searchHistory 필드 설정
                    .build();
            searchHistory.getPlaceList().add(placeInfo);
        }
        searchHistoryRepository.save(searchHistory);
    }

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