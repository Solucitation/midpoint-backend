package com.solucitation.midpoint_backend.domain.history2.repository;

import com.solucitation.midpoint_backend.domain.history2.entity.SearchHistoryV2;
import com.solucitation.midpoint_backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchHistoryRepositoryV2 extends JpaRepository<SearchHistoryV2, Long> {
    List<SearchHistoryV2> findByMemberOrderBySearchDateDesc(Member member);
}