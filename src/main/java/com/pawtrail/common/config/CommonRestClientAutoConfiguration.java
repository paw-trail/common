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
import org.springframework.context.annotation.Primary;
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
 * 빌더가 셋인 이유는 쓰임이 셋으로 갈리기 때문입니다.
 *
 *   defaultRestClientBuilder    아무것도 얹지 않은 맨 빌더. @Primary
 *   internalRestClientBuilder   우리 서비스를 부를 때
 *   externalRestClientBuilder   바깥 API 를 부를 때
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
     * 아무것도 얹지 않은 맨 빌더입니다. 우리 코드는 이것을 쓰지 않습니다.
     *
     * 이 빈이 있는 이유는 하나입니다.
     * RestClient.Builder 를 타입으로 찾는 라이브러리에게 답을 하나로 정해 주기 위해서입니다.
     *
     * * 없으면 무슨 일이 나는지입니다.
     *   유레카 클라이언트가 자기 HTTP 호출에 이 타입을 씁니다.
     *
     *     ObjectProvider&lt;RestClient.Builder&gt;.getIfAvailable(RestClient::builder)
     *
     *   후보가 0개면 기본값을 만들어 쓰고 1개면 그것을 쓰지만,
     *   2개 이상인데 @Primary 가 없으면 NoUniqueBeanDefinitionException 을 던집니다.
     *   0.0.10 에서 빌더를 둘 넣으면서 0개였던 자리가 2개가 되었고,
     *   그때부터 유레카 등록과 하트비트가 매번 실패했습니다.
     *   게이트웨이가 서비스를 못 찾아 503 이 나는데도
     *   /actuator/health 는 UP 이라 한동안 드러나지 않았습니다.
     *   유레카 헬스 컴포넌트가 UNKNOWN 이면 전체 판정에서 무시되기 때문입니다.
     *
     * * 왜 맨 빌더인지입니다.
     *   유레카는 요청 팩터리를 자기 EurekaClientHttpRequestFactorySupplier 로 따로 넣습니다.
     *   그래서 여기에 타임아웃을 얹어도 유레카에는 반영되지 않고,
     *   대신 이 빌더를 쓰게 될 다른 라이브러리의 동작만 바꿉니다.
     *   아무것도 얹지 않으면 그쪽이 스스로 만들었을 RestClient.builder() 와 같아집니다.
     *
     * * 왜 이름이 restClientBuilder 가 아닌지입니다.
     *   스프링 부트의 RestClientAutoConfiguration 이 그 이름을 씁니다.
     *   같은 이름으로 두면 BeanDefinitionOverrideException 으로 기동이 실패합니다.
     *
     * * 대신 치르는 것입니다.
     *   @Primary 가 있으므로 @Qualifier 를 빠뜨리면 이 맨 빌더가 조용히 주입됩니다.
     *   그 빌더에는 로드밸런서 인터셉터가 없어 lb:// 주소를 풀지 못하고,
     *   기동이 아니라 실제로 호출하는 순간에 실패합니다.
     *   빠뜨린 것이 늦게 드러나는 것을 알고 받아들인 것입니다.
     *   provider 를 만들 때 @Qualifier 가 있는지 반드시 확인해야 합니다.
     */
    @Bean
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder defaultRestClientBuilder() {
        return RestClient.builder();
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
     *   같은 타입의 빈이 이미 여럿이라 조건이 언제나 거짓이 되어
     *   이 빈이 아예 만들어지지 않습니다.
     *
     * * 주입할 때 @Qualifier("internalRestClientBuilder") 를 반드시 붙여야 합니다.
     *   빠뜨리면 defaultRestClientBuilder 가 조용히 들어옵니다.
     *   롬복의 @RequiredArgsConstructor 로는 @Qualifier 를 붙일 수 없으므로
     *   provider 는 생성자를 손으로 씁니다.
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
     * 프로토타입인 이유와 @Qualifier 가 필요한 이유는 위 빌더와 같습니다.
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
     *
     * * defaultRestClientBuilder 에는 이 팩터리를 걸지 않습니다.
     *   그 빌더를 쓰는 것은 우리 코드가 아니라 유레카 같은 라이브러리이고,
     *   그쪽은 자기 요청 팩터리를 따로 넣기 때문입니다.
     */
    private ClientHttpRequestFactory timeoutFactory(RestClientProperties properties) {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(HttpClientSettings.defaults()
                        .withTimeouts(properties.connectTimeout(), properties.readTimeout()));
    }
}
