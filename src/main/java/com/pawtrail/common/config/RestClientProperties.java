package com.pawtrail.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 서비스 간 호출에 쓰는 시간 설정입니다.
 *
 * 검증을 붙이지 않고 기본값을 둡니다.
 * 이 클래스는 공통 모듈에 있어 서비스 열일곱 개가 전부 받게 되는데,
 * 값이 없을 때 기동을 막으면 config 저장소 반영이 늦은 서비스가 전부 못 뜹니다.
 * 없어도 합리적인 기본값이 있는 값이므로 순서 의존을 만들지 않습니다.
 *
 * StorageProperties 와는 판단이 다릅니다.
 * 그쪽은 버킷 이름과 리전이 없으면 아예 못 도는 값이라 기동에서 막는 편이 맞습니다.
 *
 * config 저장소 1계층에 값을 두면 그것이 이기고,
 * 서비스마다 달라야 하면 2계층에서 덮어씁니다.
 * place 는 단순 조회라 짧아도 되지만 verdict 는 판정 계산에 policy 조회까지 하고,
 * LLM 을 부르는 자리는 수십 초가 걸립니다.
 *
 * @param connectTimeout 연결을 맺기까지 기다리는 시간입니다.
 *                       상대가 떠 있지 않으면 여기서 걸리므로 짧게 둡니다.
 * @param readTimeout    응답을 기다리는 시간입니다.
 *                       상대가 느리게 답하는 경우라 연결보다 길게 둡니다.
 */
@ConfigurationProperties(prefix = "app.rest-client")
public record RestClientProperties(

        @DefaultValue("2s") Duration connectTimeout,

        @DefaultValue("5s") Duration readTimeout
) {
}
