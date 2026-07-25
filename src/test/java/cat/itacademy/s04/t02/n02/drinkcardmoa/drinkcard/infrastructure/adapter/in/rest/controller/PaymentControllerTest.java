package cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.controller;

import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.query.GetPaymentStatusQuery;
import cat.itacademy.s04.t02.n02.drinkcardmoa.iam.application.port.out.TokenService;
import cat.itacademy.s04.t02.n02.drinkcardmoa.iam.infrastructure.adapter.out.security.JwtAuthenticationFilter;
import cat.itacademy.s04.t02.n02.drinkcardmoa.iam.infrastructure.config.SecurityConfiguration;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.command.CreatePaymentCheckoutCommand;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.command.ProcessPaymentWebhookCommand;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.query.ListCurrentVolunteerPaymentsQuery;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.result.CreatePaymentCheckoutResult;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.result.PaymentStatusResult;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.result.PaymentSummaryResult;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.usecase.CreatePaymentCheckoutUseCase;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.usecase.GetPaymentStatusUseCase;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.usecase.ListCurrentVolunteerPaymentsUseCase;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.usecase.ProcessPaymentWebhookUseCase;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.mapper.PaymentControllerMapper;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.config.PaymentProperties;
import cat.itacademy.s04.t02.n02.drinkcardmoa.shared.application.dto.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({PaymentControllerMapper.class, PaymentProperties.class, SecurityConfiguration.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.payment.frontend-success-url=http://localhost:3000/payment/success")
class PaymentControllerTest {

    private static final String PAYMENT_SUCCESS_URL = "http://localhost:3000/payment/success";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreatePaymentCheckoutUseCase createPaymentCheckoutUseCase;

    @MockitoBean
    private ListCurrentVolunteerPaymentsUseCase listCurrentVolunteerPaymentsUseCase;

    @MockitoBean
    private ProcessPaymentWebhookUseCase processPaymentWebhookUseCase;

    @MockitoBean
    private GetPaymentStatusUseCase getPaymentStatusUseCase;

    @MockitoBean
    private TokenService tokenService;

    @Test
    void createCheckout_ReturnsCreatedCheckoutResponse() throws Exception {
        String volunteerId = "4f0a8db1-63a7-4997-944c-9f2f6b82e6d1";
        String idempotencyKey = "checkout-request-123";

        CreatePaymentCheckoutResult result = new CreatePaymentCheckoutResult(
                "payment-123",
                "https://checkout.sumup.com/checkout-123",
                "PENDING",
                BigDecimal.valueOf(10)
        );

        when(createPaymentCheckoutUseCase.execute(
                new CreatePaymentCheckoutCommand(volunteerId, PAYMENT_SUCCESS_URL)
        )).thenReturn(result);

        String requestBody = objectMapper.writeValueAsString(new CreateCheckoutJson(
                idempotencyKey
        ));

        mockMvc.perform(post("/api/v1/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(user(volunteerId).roles("VOLUNTEER"))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value("payment-123"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.sumup.com/checkout-123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(10));

        ArgumentCaptor<CreatePaymentCheckoutCommand> commandCaptor =
                ArgumentCaptor.forClass(CreatePaymentCheckoutCommand.class);

        verify(createPaymentCheckoutUseCase).execute(commandCaptor.capture());

        CreatePaymentCheckoutCommand command = commandCaptor.getValue();

        assertEquals(volunteerId, command.volunteerId());
        assertEquals(PAYMENT_SUCCESS_URL, command.redirectUrl());
    }

    @Test
    void createCheckout_WhenAnonymous_Returns401() throws Exception {
        String requestBody = objectMapper.writeValueAsString(new CreateCheckoutJson("checkout-request-123"));

        mockMvc.perform(post("/api/v1/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCheckout_WhenWrongRole_Returns403() throws Exception {
        String requestBody = objectMapper.writeValueAsString(new CreateCheckoutJson("checkout-request-123"));

        mockMvc.perform(post("/api/v1/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(user("some-user").roles("OTHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sumUpWebhook_WhenCheckoutStatusChangedEventHasCheckoutId_ReturnsNoContentAndProcessesWebhook() throws Exception {
        String providerCheckoutId = "checkout-123";

        String requestBody = """
                {
                  "event_type": "CHECKOUT_STATUS_CHANGED",
                  "id": "checkout-123"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/sumup/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ProcessPaymentWebhookCommand> commandCaptor =
                ArgumentCaptor.forClass(ProcessPaymentWebhookCommand.class);

        verify(processPaymentWebhookUseCase).execute(commandCaptor.capture());

        assertEquals(providerCheckoutId, commandCaptor.getValue().providerCheckoutId());
    }

    @Test
    void listCurrentVolunteerPayments_WhenPaymentsExist_ShouldReturnPagedPaymentResponse() throws Exception {
        String authenticatedVolunteerId = "4f0a8db1-63a7-4997-944c-9f2f6b82e6d1";
        String ignoredVolunteerId = "8799df50-d517-4693-9e46-51b537c305a2";
        Instant createdAt = Instant.parse("2026-05-19T20:00:00Z");
        Instant paidAt = Instant.parse("2026-05-19T20:05:00Z");

        PaymentSummaryResult payment = new PaymentSummaryResult(
                "7aab22f8-60d3-4700-8ba6-b35e67dfacb6",
                authenticatedVolunteerId,
                null,
                null,
                null,
                BigDecimal.valueOf(10),
                "SUCCESS",
                "checkout-id",
                "https://checkout.example.test",
                paidAt,
                createdAt
        );

        when(listCurrentVolunteerPaymentsUseCase.execute(new ListCurrentVolunteerPaymentsQuery(
                authenticatedVolunteerId,
                1,
                10,
                "amount,asc"
        ))).thenReturn(new PageResult<>(List.of(payment), 1, 10, 25, 3));

        mockMvc.perform(get("/api/v1/payments/me")
                        .param("volunteerId", ignoredVolunteerId)
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "amount,asc")
                        .with(user(authenticatedVolunteerId).roles("VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].paymentId").value(payment.paymentId()))
                .andExpect(jsonPath("$.content[0].volunteerId").value(authenticatedVolunteerId))
                .andExpect(jsonPath("$.content[0].amount").value(10))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].providerCheckoutId").value("checkout-id"))
                .andExpect(jsonPath("$.content[0].providerCheckoutUrl").value("https://checkout.example.test"))
                .andExpect(jsonPath("$.content[0].paidAt").value("2026-05-19T20:05:00Z"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-05-19T20:00:00Z"));

        ArgumentCaptor<ListCurrentVolunteerPaymentsQuery> queryCaptor =
                ArgumentCaptor.forClass(ListCurrentVolunteerPaymentsQuery.class);
        verify(listCurrentVolunteerPaymentsUseCase).execute(queryCaptor.capture());

        ListCurrentVolunteerPaymentsQuery query = queryCaptor.getValue();

        assertAll(
                () -> assertEquals(authenticatedVolunteerId, query.volunteerId()),
                () -> assertEquals(1, query.page()),
                () -> assertEquals(10, query.size()),
                () -> assertEquals("amount,asc", query.sort())
        );
    }

    @Test
    void listCurrentVolunteerPayments_WhenNoQueryParametersProvided_ShouldUseDefaults() throws Exception {
        String authenticatedVolunteerId = "4f0a8db1-63a7-4997-944c-9f2f6b82e6d1";

        when(listCurrentVolunteerPaymentsUseCase.execute(new ListCurrentVolunteerPaymentsQuery(
                authenticatedVolunteerId,
                0,
                20,
                "createdAt,desc"
        ))).thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/payments/me")
                        .with(user(authenticatedVolunteerId).roles("VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(listCurrentVolunteerPaymentsUseCase).execute(new ListCurrentVolunteerPaymentsQuery(
                authenticatedVolunteerId,
                0,
                20,
                "createdAt,desc"
        ));
    }

    @Test
    void getPaymentStatus_WhenVolunteerIsAuthenticated_ReturnsPaymentStatusResponse() throws Exception {
        String volunteerId = "4f0a8db1-63a7-4997-944c-9f2f6b82e6d1";
        String paymentId = "payment-123";

        when(getPaymentStatusUseCase.execute(new GetPaymentStatusQuery(paymentId, volunteerId)))
                .thenReturn(new PaymentStatusResult(paymentId, "SUCCESS", BigDecimal.valueOf(10)));

        mockMvc.perform(get("/api/v1/payments/{paymentId}/status", paymentId)
                        .with(user(volunteerId).roles("VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(10));

        ArgumentCaptor<GetPaymentStatusQuery> queryCaptor =
                ArgumentCaptor.forClass(GetPaymentStatusQuery.class);

        verify(getPaymentStatusUseCase).execute(queryCaptor.capture());

        GetPaymentStatusQuery query = queryCaptor.getValue();

        assertAll(
                () -> assertEquals(paymentId, query.paymentId()),
                () -> assertEquals(volunteerId, query.volunteerId())
        );
    }

    @Test
    void getPaymentStatus_WhenAnonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{paymentId}/status", "payment-123"))
                .andExpect(status().isUnauthorized());

        verify(getPaymentStatusUseCase, never()).execute(any());
    }

    @Test
    void getPaymentStatus_WhenWrongRole_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{paymentId}/status", "payment-123")
                        .with(user("some-user").roles("OTHER")))
                .andExpect(status().isForbidden());

        verify(getPaymentStatusUseCase, never()).execute(any());
    }

    private record CreateCheckoutJson(
            String idempotencyKey
    ) {
    }
}
