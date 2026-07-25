package cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.service;

import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.dto.command.ProcessPaymentWebhookCommand;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.in.usecase.ProcessPaymentWebhookUseCase;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.DrinkCardAccountRepository;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.EventPublisher;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.PaymentRepository;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.payment.PaymentGateway;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.application.port.out.payment.PaymentGatewayStatus;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.aggregate.DrinkCardAccount;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.aggregate.Payment;
import cat.itacademy.s04.t02.n02.drinkcardmoa.drinkcard.domain.model.valueobject.Card;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ProcessPaymentWebhookService implements ProcessPaymentWebhookUseCase {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProcessPaymentWebhookService.class);

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final DrinkCardAccountRepository drinkCardAccountRepository;
    private final TransactionTemplate transactionTemplate;
    private final EventPublisher eventPublisher;

    @Override
    public void execute(ProcessPaymentWebhookCommand cmd) {

        boolean shouldProcess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            return paymentRepository.findByProviderCheckoutId(cmd.providerCheckoutId())
                    .map(payment -> {
                        if (payment.isFinalized()) {
                            log.info(
                                    "Ignoring SumUp webhook because payment is already finalized. checkoutId={}, paymentId={}, status={}",
                                    cmd.providerCheckoutId(),
                                    payment.getPaymentId().asString(),
                                    payment.getStatus()
                            );
                            return false;
                        }
                        return true;
                    })
                    .orElseGet(() -> {
                        log.warn("Ignoring SumUp webhook for unknown payment. CheckoutId: {}", cmd.providerCheckoutId());
                        return false;
                    });
        }));

        if(!shouldProcess) {
            return;
        }

        PaymentGatewayStatus providerStatus = paymentGateway.fetchCheckoutStatus(cmd.providerCheckoutId());

        transactionTemplate.executeWithoutResult(
                status -> applyProviderStatus(cmd.providerCheckoutId(), providerStatus));
    }


        private void applyProviderStatus (String providerCheckoutId, PaymentGatewayStatus providerStatus){
            Payment payment = paymentRepository.findByProviderCheckoutId(providerCheckoutId)
                    .filter(p -> !p.isFinalized())
                    .orElse(null);

            if (payment == null) {
                log.info("The payment with provider checkout id: {} was not found or already finalized", providerCheckoutId);
                return;
            }

            switch (providerStatus) {
                case PAID -> {
                    Optional<DrinkCardAccount> account = drinkCardAccountRepository.findByVolunteerId(payment.getVolunteerId());

                    if (account.isEmpty()) {
                        log.warn("Ignoring SumUp webhook for unknown volunteer id: {}", payment.getVolunteerId());
                        return;
                    }

                    DrinkCardAccount existingAccount = account.get();

                    payment.markAsSuccess();
                    existingAccount.purchaseCard(Card.newCard(), Instant.now());

                    drinkCardAccountRepository.save(existingAccount);
                    paymentRepository.save(payment);

                    existingAccount.getDomainEvents().forEach(eventPublisher::publish);
                }

                case FAILED -> {
                    payment.markAsFailed();
                    paymentRepository.save(payment);
                }

                case PENDING -> {
                    log.info("SumUp checkout is still pending: {}", providerCheckoutId);
                }

                case UNKNOWN -> {
                    log.warn("Ignoring unsupported SumUp checkout status for checkout id: {}", providerCheckoutId);
                }
            }

            log.info("Payment status synchronized for checkout id: {}", providerCheckoutId);
        }
    }
