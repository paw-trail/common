package com.pawtrail.common.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

// API 공통 응답에 PageInfo의 정보를 담을 수 있도록 함
public record PageResponse<T>(
    List<T> content,
    PageInfo page
) {
    public record PageInfo(
            int number,
            int size,
            long totalElements,
            int totalPages
    ){}

    // E = 조회 결과 타입(주로 엔티티), R = 응답 DTO 타입
    // 엔티티 타입에서 DTO 타입으로 전환한 뒤 반환
    public static <E, R> PageResponse<R> from(Page<E> page,
                                              Function<? super E, ? extends R> mapper) {
        return new PageResponse<>(
                // Page 안에서 데이터 리스트 List<E>를 가져온 뒤, Stream을 통해 E를 R로 바꾸고 List<R>로 묶음
                page.getContent().stream().<R>map(mapper).toList(),

                // Paging에 필요한 메타데이터(PageInfo)를 조립
                new PageInfo(page.getNumber(), page.getSize(),
                        page.getTotalElements(), page.getTotalPages())
        );
    }

    // 이미 DTO로 조회된 Page를 그대로 감쌀 때 (QueryDSL 프로젝션 등)
    public static <T> PageResponse<T> from(Page<T> page) {
        // 첫번째 <E, R> PageResponse의 from을 재활용
        return from(page, t -> t);
    }
}
