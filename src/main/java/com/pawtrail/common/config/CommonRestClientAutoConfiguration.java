package com.pawtrail.common.config;

import com.pawtrail.common.security.interceptor.RestClientAuthInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
     * @ConditionalOnMissingBean 을 붙이지 않습니다.
     * 스프링 부트가 이미 RestClient.Builder 빈을 하나 만들어 두므로
     * 붙이면 조건이 거짓이 되어 이 빈이 아예 만들어지지 않습니다.
     *
     * 그래서 이 타입의 빈이 셋 공존합니다.
     * 부트의 restClientBuilder, 여기의 둘입니다.
     * 이름이 다르므로 서로 죽이지 않고, 주입할 때 @Qualifier 로 고릅니다.
     *
     * @Primary 를 두지 않은 것도 의도입니다.
     * 두면 @Qualifier 를 빠뜨렸을 때 이 빌더가 조용히 주입되는데,
     * 바깥 API 를 부르는 자리에 들어가면 호출할 때에야 드러납니다.
     * 둘 다 명시하게 하면 빠뜨렸을 때 기동에서 걸립니다.
     */
    @Bean
    @LoadBalanced
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
     */
    @Bean
    public RestClient.Builder externalRestClientBuilder(RestClientProperties properties) {
        return RestClient.builder()
                .requestFactory(timeoutFactory(properties));
    }

    /**
     * 시간 제한을 건 요청 팩터리를 만듭니다.
     *
     * SimpleClientHttpRequestFactory 는 java.net.HttpURLConnection 을 씁니다.
     * 커넥션 풀이 없어 요청마다 연결을 새로 맺습니다.
     *
     * 부트가 클래스패스를 보고 구현을 골라 주는 빌더가 따로 있으나
     * Boot 4.1.1 · Spring 7.0.9 에서는 spring-web 에도 spring-boot 에도
     * 그 클래스가 없어 쓸 수 없었습니다. jar 안을 직접 확인했습니다.
     *
     * 커넥션 풀이 필요해지면 HttpComponentsClientHttpRequestFactory 로 바꾸고
     * httpclient5 의존성을 더하면 됩니다.
     * 빌더가 이 한 곳에 모여 있으므로 여기만 고치면 열두 서비스에 함께 반영됩니다.
     *
     * 빌더 둘이 팩터리를 각각 만듭니다.
     * 하나를 공유하면 한쪽에서 설정을 바꿨을 때 다른 쪽까지 따라 바뀝니다.
     */
    private ClientHttpRequestFactory timeoutFactory(RestClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
