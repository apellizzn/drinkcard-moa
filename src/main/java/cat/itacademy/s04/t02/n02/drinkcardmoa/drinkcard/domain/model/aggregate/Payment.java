package cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.aggregate;

import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.valueobject.PaymentID;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.valueobject.PaymentStatus;
import cat.itacademy.s04.t02.n02.drinkcardmoa.shared.domain.VolunteerID;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.exception.InvalidPaymentStateException;

import java.math.BigDecimal;
import java.time.Instant;

public class Payment {

    private final PaymentID paymentId;
    private final VolunteerID volunteerId;
    private final String idempotencyKey;
    private final BigDecimal amount;
    private PaymentStatus status;
    private String providerCheckoutId;
    private String providerCheckoutUrl;
    private Instant paidAt;
    private final Instant createdAt;
    private Instant expiresAt;
    private Instant providerCreatedAt;


    private Payment(
            PaymentID paymentId,
            VolunteerID volunteerId,
            String idempotencyKey,
            BigDecimal amount,
            PaymentStatus status,
            String providerCheckoutId,
            String providerCheckoutUrl,
            Instant paidAt,
            Instant createdAt,
            Instant expiresAt,
            Instant providerCreatedAt) {
        this.paymentId = paymentId;
        this.volunteerId = volunteerId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = status;
        this.providerCheckoutId = providerCheckoutId;
        this.providerCheckoutUrl = providerCheckoutUrl;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.providerCreatedAt = providerCreatedAt;
    }

    public static Payment pending(VolunteerID volunteerId, BigDecimal amount, String idempotencyKey, Instant createdAt, Instant expiresAt) {
        return new Payment(
                PaymentID.generate(),
                volunteerId,
                idempotencyKey,
                amount,
                PaymentStatus.PENDING,
                null,
                null,
                null,
                createdAt,
                expiresAt,
                null
        );
    }

    public static Payment rehydrate(
            PaymentID paymentId,
            VolunteerID volunteerId,
            String idempotencyKey,
            BigDecimal amount,
            PaymentStatus status,
            String providerCheckoutId,
            String providerCheckoutUrl,
            Instant paidAt,
            Instant createdAt,
            Instant expiresAt,
            Instant providerCreatedAt
    ) {
        return new Payment(
                paymentId,
                volunteerId,
                idempotencyKey,
                amount,
                status,
                providerCheckoutId,
                providerCheckoutUrl,
                paidAt,
                createdAt,
                expiresAt,
                providerCreatedAt
        );
    }

    public void attachProviderCheckoutId(String providerCheckoutId) {
        if (this.providerCheckoutId != null && !providerCheckoutId.isBlank())
            throw new InvalidPaymentStateException("Payment already has a provider checkout ID");

        this.providerCheckoutId = providerCheckoutId;
    }

    public void attachProviderCheckoutUrl(String providerCheckoutUrl) {
        if (this.providerCheckoutUrl != null && !providerCheckoutUrl.isBlank())
            throw new InvalidPaymentStateException("Payment already has a provider checkout URL");

        this.providerCheckoutUrl = providerCheckoutUrl;
    }

    public void attachProviderCreatedAt(Instant providerCreatedAt) {
        if (this.providerCreatedAt != null)
            throw new InvalidPaymentStateException("Payment already has a provider creation time");

        this.providerCreatedAt = providerCreatedAt;
    }

    public void markAsSuccess() {
        if (this.status == PaymentStatus.SUCCESS) {
            throw new InvalidPaymentStateException("Payment is already successful");
        }

        this.status = PaymentStatus.SUCCESS;
        this.paidAt = Instant.now();
    }

    public void markAsFailed() {
        if(this.status == PaymentStatus.SUCCESS)
            throw new InvalidPaymentStateException("Payment is already finalized");

        this.status = PaymentStatus.FAILED;
    }

    public boolean isFinalized() {
        return this.status == PaymentStatus.SUCCESS;
    }

    public boolean isSuccess() {
        return this.status == PaymentStatus.SUCCESS;
    }

    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    public PaymentID getPaymentId() {
        return paymentId;
    }
    public VolunteerID getVolunteerId() {
        return volunteerId;
    }
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public PaymentStatus getStatus() {
        return status;
    }
    public String getProviderCheckoutId() {
        return providerCheckoutId;
    }
    public String getProviderCheckoutUrl() {
        return providerCheckoutUrl;
    }
    public Instant getPaidAt() {
        return paidAt;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public Instant getProviderCreatedAt() {
        return providerCreatedAt;
    }
}
