package cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.config;

import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.payment.PaymentGateway;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.out.payment.SumUpPaymentAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({SumUpProperties.class, PaymentProperties.class})
public class PaymentBeanConfiguration {

    @Bean
    PaymentGateway paymentGateway(SumUpProperties properties) {
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();

        return new SumUpPaymentAdapter(restClient, properties);
    }
}
