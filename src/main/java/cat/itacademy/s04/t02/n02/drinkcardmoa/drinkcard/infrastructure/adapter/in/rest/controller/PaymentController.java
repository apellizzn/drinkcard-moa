package cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.controller;

import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.command.ProcessPaymentWebhookCommand;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.query.ListCurrentVolunteerPaymentsQuery;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.result.ConfirmPaymentResult;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.result.CreatePaymentCheckoutResult;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.result.PaymentStatusResult;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.result.PaymentSummaryResult;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.usecase.*;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.query.GetPaymentStatusQuery;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.dto.request.CreatePaymentCheckoutRequest;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.dto.request.SumUpWebhookRequest;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.dto.response.ConfirmPaymentResponse;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.dto.response.CreatePaymentCheckoutResponse;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.dto.response.PaymentStatusResponse;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.dto.response.PaymentSummaryResponse;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.adapter.in.rest.mapper.PaymentControllerMapper;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.infrastructure.config.PaymentProperties;
import cat.itacademy.s04.t02.n02.drinkcardmoa.shared.application.dto.PageResult;
import cat.itacademy.s04.t02.n02.drinkcardmoa.shared.infrastructure.adapter.in.rest.dto.response.PageResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@AllArgsConstructor
public class PaymentController {

    private static final String SUM_UP_EVENT_TYPE = "CHECKOUT_STATUS_CHANGED";

    private final CreatePaymentCheckoutUseCase createPaymentCheckoutUseCase;
    private final ProcessPaymentWebhookUseCase processPaymentWebhookUseCase;
    private final ListCurrentVolunteerPaymentsUseCase listCurrentVolunteerPaymentsUseCase;
    private final GetPaymentStatusUseCase getPaymentStatusUseCase;
    private final PaymentControllerMapper mapper;
    private final PaymentProperties paymentProperties;


    @PostMapping("/checkout")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<CreatePaymentCheckoutResponse> createCheckout(@RequestBody CreatePaymentCheckoutRequest request, Authentication authentication) {

        CreatePaymentCheckoutResult result = createPaymentCheckoutUseCase.execute(mapper.toCommand(request, authentication.getName(), paymentProperties.getFrontendSuccessUrl()));

        return ResponseEntity.status(201).body(mapper.toResponse(result));
    }

    @PostMapping("/sumup/webhook")
    public ResponseEntity<Void> sumUpWebhook(@RequestBody SumUpWebhookRequest request) {

        processPaymentWebhookUseCase.execute(new ProcessPaymentWebhookCommand(request.id()));

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<PageResponse<PaymentSummaryResponse>> listCurrentVolunteerPayments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        ListCurrentVolunteerPaymentsQuery query = new ListCurrentVolunteerPaymentsQuery(authentication.getName(), page, size, sort);

        PageResult<PaymentSummaryResult> result = listCurrentVolunteerPaymentsUseCase.execute(query);

        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @GetMapping("/{paymentId}/status")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable String paymentId, Authentication authentication) {
        GetPaymentStatusQuery query = new GetPaymentStatusQuery(paymentId, authentication.getName());

        PaymentStatusResult result = getPaymentStatusUseCase.execute(query);

        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
