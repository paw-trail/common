package com.pawtrail.common.config;

import com.pawtrail.common.security.interceptor.RestClientAuthInterceptor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 서비스가 다른 서비스를 부를 때 쓰는 빌더를 만듭니다.
 *
 * 이 프로젝트에서 서비스 간 호출이 처음 필요해진 것은 user 부터입니다.
 * auth 는 "다른 서비스를 한 번도 호출하지 않는다" 가 설계였습니다.
 * 그래서 RestClientAuthInterceptor 는 클래스만 있고 어디에도 연결돼 있지 않았습니다.
 *
 * 여기에 두는 이유는 인터셉터가 서비스와 무관하기 때문입니다.
 * 하는 일이 SecurityContext 에서 사용자를 꺼내 헤더 둘을 붙이는 것뿐이고
 * 서비스 이름도, 부르는 대상도, 경로도 보지 않습니다.
 * 그 SecurityContext 를 채우는 HeaderAuthenticationFilter 도 공통 모듈에 있으므로
 * 받는 쪽과 보내는 쪽이 대칭이 됩니다.
 * 클래스만 공통에 두고 연결은 각자 하게 하면 같은 코드가 열두 번 반복됩니다.
 *
 * 빌더가 둘인 이유는 @LoadBalanced 가 붙은 빌더로는 바깥 API 를 부를 수 없기 때문입니다.
 * 그 빌더는 주소를 서비스 이름으로 보고 유레카에서 찾으려 합니다.
 *
 * 무엇을 어디에 부르고 응답을 어떻게 다룰지는 각 서비스가 정합니다.
 * 특히 실패했을 때의 처리는 여기 두지 않습니다.
 * 같은 서비스 안에서도 API 마다 다르기 때문입니다.
 * 마이페이지의 후기 수는 실패하면 그 값만 null 로 두고 나머지를 내려보내지만,
 * 방문 기록의 판정은 실패하면 요청 자체를 실패시킵니다.
 * 판정 스냅샷은 나중에 고칠 방법이 없어 틀린 값을 남기면 안 되기 때문입니다.
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(RestClientProperties.class)
public class CommonRestClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestClientAuthInterceptor restClientAuthInterceptor() {
        return new RestClientAuthInterceptor();
    }

    /**
     * 다른 서비스를 부를 때 쓰는 빌더입니다.
     *
     * lb://place-service 처럼 서비스 이름으로 주소를 씁니다.
     * @LoadBalanced 가 그 이름을 유레카에서 찾아 실제 주소로 바꿉니다.
     *
     * 인터셉터가 붙어 있어 나가는 요청에 X-User-Id 와 X-User-Role 이 실립니다.
     * 없으면 받는 쪽의 HeaderAuthenticationFilter 가 심을 값이 없어
     * 그쪽에서 만든 엔티티의 createdBy 가 전부 SYSTEM 으로 남습니다.
     *
     * * 프로토타입인 이유입니다.
     *   RestClient.Builder 는 자기 자신을 고치고 자기를 돌려주는 물건입니다.
     *   싱글턴으로 두면 주입받은 모두가 같은 인스턴스를 나눠 쓰게 되어
     *   한 provider 가 baseUrl 을 걸면 다른 provider 의 것까지 바뀝니다.
     *   생성자에서 걸자마자 build() 하면 결과는 맞지만 그것은 순서에 기댄 것이고,
     *   빌더를 필드에 들고 있다가 늦게 build() 하는 코드가 하나 생기면
     *   오류 없이 엉뚱한 서비스로 요청이 갑니다.
     *   스프링 부트가 자기 RestClient.Builder 빈을 프로토타입으로 두는 이유도 같습니다.
     *
     * * @ConditionalOnMissingBean 을 붙이지 않습니다.
     *   spring-boot-restclient 의 RestClientAutoConfiguration 이
     *   같은 타입의 빈을 하나 정의합니다.
     *   그쪽이 먼저 평가되면 이 조건이 거짓이 되어 이 빈이 아예 만들어지지 않습니다.
     *   자동 설정 사이의 평가 순서에 기대지 않으려고 조건을 걸지 않았습니다.
     *
     * * @Primary 를 두지 않은 것도 의도입니다.
     *   두면 @Qualifier 를 빠뜨렸을 때 이 빌더가 조용히 주입되는데,
     *   바깥 API 를 부르는 자리에 들어가면 호출할 때에야 드러납니다.
     *   둘 다 명시하게 하면 빠뜨렸을 때 기동에서 걸립니다.
     */
    @Bean
    @LoadBalanced
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder internalRestClientBuilder(
            RestClientAuthInterceptor authInterceptor,
            RestClientProperties properties) {

        return RestClient.builder()
                .requestInterceptor(authInterceptor)
                .requestFactory(timeoutFactory(properties));
    }

    /**
     * 바깥 API 를 부를 때 쓰는 빌더입니다.
     *
     * 카카오맵, 기상청, 관광공사처럼 우리가 만들지 않은 곳입니다.
     * ingest 와 congestion, route 가 쓰게 됩니다.
     *
     * 인터셉터를 붙이지 않습니다.
     * 바깥에 우리 사용자 식별자를 보낼 이유가 없습니다.
     *
     * @LoadBalanced 도 붙이지 않습니다.
     * 붙이면 https://apis.data.go.kr 같은 주소를 서비스 이름으로 보고
     * 유레카에서 찾으려다 실패합니다.
     *
     * 프로토타입인 이유는 위 빌더와 같습니다.
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder externalRestClientBuilder(RestClientProperties properties) {
        return RestClient.builder()
                .requestFactory(timeoutFactory(properties));
    }

    /**
     * 시간 제한을 건 요청 팩터리를 만듭니다.
     *
     * detect() 가 클래스패스를 보고 구현을 고릅니다.
     * httpComponents → jetty → reactor → jdk → simple 순으로 보는데,
     * jdk 의 판단 근거인 java.net.http.HttpClient 는 JDK 11 부터 표준이라
     * 아무 의존성도 더하지 않은 지금은 항상 jdk 가 골라집니다.
     * HttpURLConnection 을 쓰는 simple 과 달리 연결을 재사용하고 PATCH 도 보냅니다.
     *
     * 나중에 서비스가 httpclient5 를 물면 그 서비스에서만 httpComponents 로 바뀝니다.
     * 이 코드는 고치지 않아도 됩니다.
     *
     * * spring.http.client.* 로 설정된 빌더 빈을 주입받지 않고 직접 detect() 합니다.
     *   시간 제한을 고치는 창구를 app.rest-client 하나로 두기 위해서입니다.
     *   빈을 받으면 같은 값을 두 곳에서 줄 수 있게 되고
     *   어느 쪽이 이기는지를 매번 설명해야 합니다.
     *   리다이렉트나 SSL 설정이 필요해지면 그때 빈을 받는 쪽으로 넓힙니다.
     *
     * * 빌더가 프로토타입이므로 주입받는 자리마다 팩터리도 새로 만들어집니다.
     *   provider 가 셋이면 HttpClient 도 셋이고 연결 풀도 각각입니다.
     *   provider 마다 부르는 서비스가 달라 어차피 연결이 갈리므로 손해가 아닙니다.
     */
    private ClientHttpRequestFactory timeoutFactory(RestClientProperties properties) {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(HttpClientSettings.defaults()
                        .withTimeouts(properties.connectTimeout(), properties.readTimeout()));
    }
}
