package com.cardbilling.collections.infrastructure.client;

import com.cardbilling.collections.infrastructure.config.CollectionsProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * The two outbound HTTP clients.
 *
 * <p>Both get explicit connect and read timeouts. A synchronous call with no read timeout is the
 * failure mode a circuit breaker cannot save you from — the breaker only ever sees a call that
 * finished, and a call that hangs forever never does.
 */
@Configuration
public class RestClientConfig {

    /**
     * Whether outbound calls carry a Keycloak token. Only ever false in tests, which stub the two
     * downstream services and have no Keycloak to get a token from. Gating the interceptor on an
     * explicit property rather than on whether an {@code OAuth2AuthorizedClientManager} bean
     * happens to exist matters: Boot auto-configures one of those on its own, so "no manager bean"
     * is not a state this service can actually reach.
     */
    private final boolean outboundAuthEnabled;

    public RestClientConfig(
            @Value("${collections.outbound-auth.enabled:true}") boolean outboundAuthEnabled) {
        this.outboundAuthEnabled = outboundAuthEnabled;
    }

    /**
     * Client-credentials only: there is no user to act on behalf of, this service authenticates as
     * itself. Boot would auto-configure a manager of its own, but deriving the grant type from
     * whatever registrations happen to be present is exactly the kind of implicit behaviour that
     * is hard to notice going wrong — this one is explicit about being client-credentials.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean("billingServiceRestClient")
    public RestClient billingServiceRestClient(
            RestClient.Builder builder,
            CollectionsProperties properties,
            ObjectProvider<OAuth2AuthorizedClientManager> authorizedClientManager) {
        return build(builder, properties.billingService(), authorizedClientManager);
    }

    @Bean("notificationServiceRestClient")
    public RestClient notificationServiceRestClient(
            RestClient.Builder builder,
            CollectionsProperties properties,
            ObjectProvider<OAuth2AuthorizedClientManager> authorizedClientManager) {
        return build(builder, properties.notificationService(), authorizedClientManager);
    }

    /**
     * Boot's own {@code RestClient.Builder} is the starting point rather than a bare
     * {@code RestClient.builder()}: it carries the application's configured message converters, so
     * an invoice date is serialised the same way on the wire as it is everywhere else in this
     * service instead of by a second, separately-defaulted Jackson.
     */
    private RestClient build(
            RestClient.Builder builder,
            CollectionsProperties.Downstream downstream,
            ObjectProvider<OAuth2AuthorizedClientManager> authorizedClientManager) {
        // HTTP/1.1 explicitly. The JDK client defaults to HTTP/2, which over plain HTTP means an
        // h2c upgrade negotiation on the first request to each origin — and the first request to
        // notification-service is a POST with a body, the case where that negotiation is least
        // well behaved. These are internal calls between four services on one network; there is
        // no multiplexing benefit to trade a connection-reset failure mode for.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(downstream.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(downstream.readTimeout());

        builder.baseUrl(downstream.baseUrl()).requestFactory(requestFactory);
        if (outboundAuthEnabled) {
            ClientHttpRequestInterceptor interceptor = new ClientCredentialsTokenInterceptor(
                    authorizedClientManager.getObject(), downstream.clientRegistrationId());
            builder.requestInterceptor(interceptor);
        }
        return builder.build();
    }
}
