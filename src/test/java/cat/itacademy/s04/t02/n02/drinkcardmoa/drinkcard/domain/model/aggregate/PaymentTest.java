package cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.aggregate;

import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.valueobject.PaymentID;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.valueobject.PaymentStatus;
import cat.itacademy.s04.t02.n02.drinkcardmoa.shared.domain.VolunteerID;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.exception.InvalidPaymentStateException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10);
    private static final String IDEMPOTENCY_KEY = "payment-123";
    private static final Duration EXPIRATION_TIME = Duration.ofMinutes(15);

    @Test
    void pending_ShouldCreatePaymentWithPendingStatus() {
        VolunteerID volunteerId = VolunteerID.generate();

        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(EXPIRATION_TIME);
        Payment payment = Payment.pending(volunteerId, AMOUNT, IDEMPOTENCY_KEY, createdAt, expiresAt);

        assertAll(
                () -> assertNotNull(payment.getPaymentId()),
                () -> assertEquals(volunteerId, payment.getVolunteerId()),
                () -> assertEquals(IDEMPOTENCY_KEY, payment.getIdempotencyKey()),
                () -> assertEquals(AMOUNT, payment.getAmount()),
                () -> Assertions.assertEquals(PaymentStatus.PENDING, payment.getStatus()),
                () -> assertNull(payment.getProviderCheckoutId()),
                () -> assertNull(payment.getProviderCheckoutUrl()),
                () -> assertNull(payment.getPaidAt()),
                () -> assertEquals(payment.getCreatedAt(), createdAt),
                () -> assertEquals(payment.getExpiresAt(), expiresAt),
                () -> assertNull(payment.getProviderCreatedAt())
        );
    }

    @Test
    void rehydrate_ShouldRestorePaymentWithGivenValues() {
        PaymentID paymentId = PaymentID.generate();
        VolunteerID volunteerId = VolunteerID.generate();
        Instant paidAt = Instant.now();
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(EXPIRATION_TIME);
        Instant providerCreatedAt = createdAt;

        Payment payment = Payment.rehydrate(
                paymentId,
                volunteerId,
                IDEMPOTENCY_KEY,
                AMOUNT,
                PaymentStatus.SUCCESS,
                "checkout-id-123",
                "https://checkout.url",
                paidAt,
                createdAt,
                expiresAt,
                providerCreatedAt
        );

        assertAll(
                () -> assertEquals(paymentId, payment.getPaymentId()),
                () -> assertEquals(volunteerId, payment.getVolunteerId()),
                () -> assertEquals(IDEMPOTENCY_KEY, payment.getIdempotencyKey()),
                () -> assertEquals(AMOUNT, payment.getAmount()),
                () -> assertEquals(PaymentStatus.SUCCESS, payment.getStatus()),
                () -> assertEquals("checkout-id-123", payment.getProviderCheckoutId()),
                () -> assertEquals("https://checkout.url", payment.getProviderCheckoutUrl()),
                () -> assertEquals(paidAt, payment.getPaidAt()),
                () -> assertEquals(createdAt, payment.getCreatedAt()),
                () -> assertEquals(expiresAt, payment.getExpiresAt()),
                () -> assertEquals(providerCreatedAt, payment.getProviderCreatedAt())
        );
    }

    @Test
    void attachProviderCheckoutId_WhenPaymentDoesNotHaveCheckoutId_ShouldAttachIt() {
        Payment payment = pendingPayment();

        payment.attachProviderCheckoutId("checkout-id-123");

        assertEquals("checkout-id-123", payment.getProviderCheckoutId());
    }

    @Test
    void attachProviderCheckoutId_WhenPaymentAlreadyHasCheckoutId_ShouldThrowException() {
        Payment payment = pendingPayment();
        payment.attachProviderCheckoutId("checkout-id-123");

        assertThrows(
                InvalidPaymentStateException.class,
                () -> payment.attachProviderCheckoutId("another-checkout-id")
        );
    }

    @Test
    void attachProviderCheckoutUrl_WhenPaymentDoesNotHaveCheckoutUrl_ShouldAttachIt() {
        Payment payment = pendingPayment();

        payment.attachProviderCheckoutUrl("https://checkout.url");

        assertEquals("https://checkout.url", payment.getProviderCheckoutUrl());
    }

    @Test
    void attachProviderCheckoutUrl_WhenPaymentAlreadyHasCheckoutUrl_ShouldThrowException() {
        Payment payment = pendingPayment();
        payment.attachProviderCheckoutUrl("https://checkout.url");

        assertThrows(
                InvalidPaymentStateException.class,
                () -> payment.attachProviderCheckoutUrl("https://another-checkout.url")
        );
    }

    @Test
    void attachProviderCreatedAt_WhenPaymentDoesNotHaveProviderCreatedAt_ShouldAttachIt() {
        Payment payment = pendingPayment();
        Instant providerCreatedAt = Instant.now();
        payment.attachProviderCreatedAt(providerCreatedAt);

        assertEquals(providerCreatedAt, payment.getProviderCreatedAt());
    }

    @Test
    void attachProviderCreatedAt_WhenPaymentAlreadyHasProviderCreatedAt_ShouldThrowException() {
        Payment payment = pendingPayment();
        payment.attachProviderCreatedAt(Instant.now());

        assertThrows(
                InvalidPaymentStateException.class,
                () -> payment.attachProviderCreatedAt(Instant.now().plusSeconds(1))
        );
    }

    @Test
    void markAsSuccess_WhenPaymentIsPending_ShouldChangeStatusToSuccessAndSetPaidAt() {
        Payment payment = pendingPayment();

        payment.markAsSuccess();

        assertAll(
                () -> assertEquals(PaymentStatus.SUCCESS, payment.getStatus()),
                () -> assertNotNull(payment.getPaidAt()),
                () -> assertTrue(payment.isSuccess()),
                () -> assertTrue(payment.isFinalized()),
                () -> assertFalse(payment.isFailed())
        );
    }

    @Test
    void markAsFailed_WhenPaymentIsPending_ShouldChangeStatusToFailed() {
        Payment payment = pendingPayment();

        payment.markAsFailed();

        assertAll(
                () -> assertEquals(PaymentStatus.FAILED, payment.getStatus()),
                () -> assertNull(payment.getPaidAt()),
                () -> assertTrue(payment.isFailed()),
                () -> assertFalse(payment.isFinalized()),
                () -> assertFalse(payment.isSuccess())
        );
    }

    @Test
    void markAsSuccess_WhenPaymentIsAlreadySuccess_ShouldThrowException() {
        Payment payment = paymentWithStatus(PaymentStatus.SUCCESS);

        assertThrows(InvalidPaymentStateException.class, payment::markAsSuccess);
    }

    @Test
    void markAsFailed_WhenPaymentIsNotPending_ShouldThrowException() {
        Payment payment = paymentWithStatus(PaymentStatus.SUCCESS);

        assertThrows(InvalidPaymentStateException.class, payment::markAsFailed);
    }

    @Test
    void isFinalized_WhenPaymentIsPending_ShouldReturnFalse() {
        Payment payment = pendingPayment();

        assertFalse(payment.isFinalized());
    }

    private static Payment pendingPayment() {
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(EXPIRATION_TIME);
        return Payment.pending(
                VolunteerID.generate(),
                AMOUNT,
                IDEMPOTENCY_KEY,
                createdAt,
                expiresAt
        );
    }

    private static Payment paymentWithStatus(PaymentStatus status) {
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(EXPIRATION_TIME);

        return Payment.rehydrate(
                PaymentID.generate(),
                VolunteerID.generate(),
                IDEMPOTENCY_KEY,
                AMOUNT,
                status,
                null,
                null,
                null,
                createdAt,
                expiresAt,
                null
        );
    }
}

