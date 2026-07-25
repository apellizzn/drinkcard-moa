package cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.service;

import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.command.ProcessPaymentWebhookCommand;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.DrinkCardAccountRepository;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.EventPublisher;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.PaymentRepository;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.payment.PaymentGateway;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.payment.PaymentGatewayStatus;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.aggregate.DrinkCardAccount;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.aggregate.Payment;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.valueobject.PaymentStatus;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.testhelper.PaymentTestBuilder;
import cat.itacademy.s04.t02.n02.drinkcardmoa.shared.domain.VolunteerID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentWebhookServiceTest {

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private DrinkCardAccountRepository drinkCardAccountRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ProcessPaymentWebhookService service;

    @Test
    void execute_WhenCheckoutIdIsUnknown_DoesNothing() {
        when(transactionTemplate.execute(any())).thenAnswer(executeTransactionCallback());

        when(paymentRepository.findByProviderCheckoutId(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(Optional.empty());


        service.execute(command());

        verify(paymentGateway, never()).fetchCheckoutStatus(any());
        verify(transactionTemplate, never()).executeWithoutResult(any());
        verify(drinkCardAccountRepository, never()).findByVolunteerId(any());
        verify(paymentRepository, never()).save(any());
        verify(drinkCardAccountRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void execute_WhenPaymentIsAlreadyFinalized_DoesNotFetchProviderStatusOrSave() {
        Payment payment = PaymentTestBuilder.aPayment()
                .withStatus(PaymentStatus.SUCCESS)
                .withPaidAt(Instant.now())
                .build();

        when(transactionTemplate.execute(any())).thenAnswer(executeTransactionCallback());

        when(paymentRepository.findByProviderCheckoutId(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(Optional.of(payment));

        service.execute(command());

        verify(paymentGateway, never()).fetchCheckoutStatus(any());
        verify(transactionTemplate, never()).executeWithoutResult(any());
        verify(drinkCardAccountRepository, never()).findByVolunteerId(any());
        verify(paymentRepository, never()).save(any());
        verify(drinkCardAccountRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void execute_WhenProviderStatusIsPaid_MarksPaymentAsSuccessAddsCreditsAndPublishesEvent() {
        VolunteerID volunteerId = VolunteerID.generate();
        Payment payment = pendingPayment(volunteerId);
        DrinkCardAccount account = DrinkCardAccount.rehydrate(1L, volunteerId, 0, null, Instant.now());

        when(transactionTemplate.execute(any())).thenAnswer(executeTransactionCallback());

        when(paymentRepository.findByProviderCheckoutId(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(PaymentGatewayStatus.PAID);
        when(drinkCardAccountRepository.findByVolunteerId(volunteerId)).thenReturn(Optional.of(account));
        doAnswer(executeWithoutResultCallback()).when(transactionTemplate).executeWithoutResult(any());

        service.execute(command());

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(5, account.getCredits());

        verify(paymentGateway).fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID);
        verify(drinkCardAccountRepository).save(account);
        verify(paymentRepository).save(payment);
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    void execute_WhenDuplicatePaidWebhookArrives_DoesNotAddCreditsAgain() {
        VolunteerID volunteerId = VolunteerID.generate();
        Payment payment = pendingPayment(volunteerId);
        DrinkCardAccount account = DrinkCardAccount.rehydrate(1L, volunteerId, 0, null, Instant.now());

        when(transactionTemplate.execute(any())).thenAnswer(executeTransactionCallback());

        when(paymentRepository.findByProviderCheckoutId(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(PaymentGatewayStatus.PAID);
        when(drinkCardAccountRepository.findByVolunteerId(volunteerId)).thenReturn(Optional.of(account));
        doAnswer(executeWithoutResultCallback()).when(transactionTemplate).executeWithoutResult(any());

        service.execute(command());
        service.execute(command());

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(5, account.getCredits());

        verify(paymentGateway, times(1)).fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID);
        verify(drinkCardAccountRepository, times(1)).save(account);
        verify(paymentRepository, times(1)).save(payment);
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    void execute_WhenPaidButDrinkCardAccountDoesNotExist_DoesNotFinalizePaymentOrSave() {
        VolunteerID volunteerId = VolunteerID.generate();
        Payment payment = pendingPayment(volunteerId);

        when(transactionTemplate.execute(any())).thenAnswer(executeTransactionCallback());

        when(paymentRepository.findByProviderCheckoutId(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(PaymentGatewayStatus.PAID);
        when(drinkCardAccountRepository.findByVolunteerId(volunteerId)).thenReturn(Optional.empty());
        doAnswer(executeWithoutResultCallback()).when(transactionTemplate).executeWithoutResult(any());

        service.execute(command());

        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(paymentGateway).fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID);
        verify(drinkCardAccountRepository).findByVolunteerId(volunteerId);
        verify(drinkCardAccountRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void execute_WhenProviderStatusIsFailed_MarksPaymentAsFailedWithoutAddingCredits() {
        Payment payment = pendingPayment(VolunteerID.generate());

        when(transactionTemplate.execute(any())).thenAnswer(executeTransactionCallback());

        when(paymentRepository.findByProviderCheckoutId(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(PaymentGatewayStatus.FAILED);
        doAnswer(executeWithoutResultCallback()).when(transactionTemplate).executeWithoutResult(any());

        service.execute(command());

        assertEquals(PaymentStatus.FAILED, payment.getStatus());

        verify(paymentGateway).fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID);
        verify(drinkCardAccountRepository, never()).findByVolunteerId(any());
        verify(drinkCardAccountRepository, never()).save(any());
        verify(paymentRepository).save(payment);
        verify(eventPublisher, never()).publish(any());
    }


    @Test
    void execute_WhenProviderStatusIsPending_DoesNotChangePayment() {
        Payment payment = pendingPayment(VolunteerID.generate());

        when(transactionTemplate.execute(any())).thenAnswer(executeTransactionCallback());

        when(paymentRepository.findByProviderCheckoutId(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(PaymentGatewayStatus.PENDING);
        doAnswer(executeWithoutResultCallback()).when(transactionTemplate).executeWithoutResult(any());

        service.execute(command());

        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(paymentGateway).fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID);
        verify(drinkCardAccountRepository, never()).findByVolunteerId(any());
        verify(paymentRepository, never()).save(any());
        verify(drinkCardAccountRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void execute_WhenProviderStatusIsUnknown_DoesNotChangePayment() {
        Payment payment = pendingPayment(VolunteerID.generate());

        when(transactionTemplate.execute(any())).thenAnswer(executeTransactionCallback());

        when(paymentRepository.findByProviderCheckoutId(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID))
                .thenReturn(PaymentGatewayStatus.UNKNOWN);
        doAnswer(executeWithoutResultCallback()).when(transactionTemplate).executeWithoutResult(any());

        service.execute(command());

        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(paymentGateway).fetchCheckoutStatus(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID);
        verify(drinkCardAccountRepository, never()).findByVolunteerId(any());
        verify(paymentRepository, never()).save(any());
        verify(drinkCardAccountRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    private ProcessPaymentWebhookCommand command() {
        return new ProcessPaymentWebhookCommand(PaymentTestBuilder.DEFAULT_PROVIDER_CHECKOUT_ID);
    }

    private Payment pendingPayment(VolunteerID volunteerId) {
        return PaymentTestBuilder.aPayment()
                .withVolunteerId(volunteerId)
                .withStatus(PaymentStatus.PENDING)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Answer<Object> executeTransactionCallback() {
        return invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        };
    }

    @SuppressWarnings("unchecked")
    private Answer<Void> executeWithoutResultCallback() {
        return invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        };
    }
}
