package cat.itacademy.s04.t02.n02.drinkcardmoa.iam.integration;

import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.DrinkCardAccountRepository;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.PaymentRepository;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.payment.PaymentGateway;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.payment.PaymentGatewayStatus;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.aggregate.DrinkCardAccount;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.aggregate.Payment;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.valueobject.PaymentStatus;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.controller.PaymentController;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.out.persistence.repository.JpaPaymentRepository;
import cat.itacademy.s04.t02.n02.drinkcardmoa.iam.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import cat.itacademy.s04.t02.n02.drinkcardmoa.iam.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import cat.itacademy.s04.t02.n02.drinkcardmoa.shared.domain.VolunteerID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerTestIT {

    private static final String EMAIL = "concurrent-admin@test.com";

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    private JpaUserRepository jpaUserRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DrinkCardAccountRepository drinkCardAccountRepository;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Test
    void testHandleFailureToSuccess() throws Exception {
        String userId = VolunteerID.generate().asString();
        String email = "userid@userid.com";

        UserJpaEntity userEntity = UserJpaEntity.create(
                userId,
                "firstName",
                "lastName",
                email,
                "hashed_password",
                "VOLUNTEER",
                "ACTIVE"
        );
        jpaUserRepository.save(userEntity);

        drinkCardAccountRepository.save(DrinkCardAccount.create(
                VolunteerID.from(userId)
        ));

        var payment = Payment.pending(
                VolunteerID.from(userId),
                new BigDecimal("10.00"),
                "test",
                Instant.now(),
                Instant.now().plus(10, ChronoUnit.HOURS)
        );
        var checkoutId = UUID.randomUUID().toString();
        payment.attachProviderCheckoutId(checkoutId);
        paymentRepository.save(payment);

        Mockito.when(paymentGateway.fetchCheckoutStatus(checkoutId)).thenReturn(PaymentGatewayStatus.FAILED);

        mockMvc.perform(post("/api/v1/payments/sumup/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "id": "%s",
                            "status": "FAILED",
                            "event_type": "CHECKOUT_STATUS_CHANGED"
                        }
                        """.formatted(checkoutId)))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        var failed = paymentRepository.findByPaymentId(payment.getPaymentId()).orElseThrow(AssertionError::new);
        assertEquals("FAILED", failed.getStatus().name());
        assertFalse(failed.isFinalized());

        Mockito.when(paymentGateway.fetchCheckoutStatus(checkoutId)).thenReturn(PaymentGatewayStatus.PAID);

        mockMvc.perform(post("/api/v1/payments/sumup/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "id": "%s",
                            "status": "SUCCESS",
                            "event_type": "CHECKOUT_STATUS_CHANGED"
                        }
                        """.formatted(checkoutId)))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        var success = paymentRepository.findByPaymentId(payment.getPaymentId()).orElseThrow(AssertionError::new);
        assertEquals("SUCCESS", success.getStatus().name());
        assertTrue(success.isFinalized());

        var updatedDrinkCardAccount = drinkCardAccountRepository.findByVolunteerId(VolunteerID.from(userId)).orElseThrow(AssertionError::new);
        assertEquals(5, updatedDrinkCardAccount.getCredits());
        assertNotNull(updatedDrinkCardAccount.getLastPurchaseTimestamp());
    }

    @Test
    void testHandleDoubleFailureNotification() throws Exception {
        String userId = VolunteerID.generate().asString();
        String email = "userid@userid.com";

        UserJpaEntity userEntity = UserJpaEntity.create(
                userId,
                "firstName",
                "lastName",
                email,
                "hashed_password",
                "VOLUNTEER",
                "ACTIVE"
        );
        jpaUserRepository.save(userEntity);

        var payment = Payment.pending(
                VolunteerID.from(userId),
                new BigDecimal("10.00"),
                "test",
                Instant.now(),
                Instant.now().plus(10, ChronoUnit.HOURS)
        );
        var checkoutId = UUID.randomUUID().toString();
        payment.attachProviderCheckoutId(checkoutId);
        paymentRepository.save(payment);

        Mockito.when(paymentGateway.fetchCheckoutStatus(checkoutId)).thenReturn(PaymentGatewayStatus.FAILED);

        mockMvc.perform(post("/api/v1/payments/sumup/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "id": "%s",
                                    "status": "FAILED",
                                    "event_type": "CHECKOUT_STATUS_CHANGED"
                                }
                                """.formatted(checkoutId)))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        var failed = paymentRepository.findByPaymentId(payment.getPaymentId()).orElseThrow(AssertionError::new);
        assertEquals("FAILED", failed.getStatus().name());
        assertFalse(failed.isFinalized());

        mockMvc.perform(post("/api/v1/payments/sumup/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "id": "%s",
                                    "status": "FAILED",
                                    "event_type": "CHECKOUT_STATUS_CHANGED"
                                }
                                """.formatted(checkoutId)))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

}
