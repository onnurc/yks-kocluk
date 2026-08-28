package com.ykskocluk.demo.integration;

import com.iyzipay.Options;
import com.iyzipay.model.Address;
import com.iyzipay.model.BasketItem;
import com.iyzipay.model.BasketItemType;
import com.iyzipay.model.Buyer;
import com.iyzipay.model.CheckoutFormInitialize;
import com.iyzipay.model.Currency;
import com.iyzipay.model.Locale;
import com.iyzipay.model.PaymentGroup;
import com.iyzipay.model.Status;
import com.iyzipay.model.Refund;
import com.iyzipay.request.CreateRefundRequest;
import com.iyzipay.request.CreateCheckoutFormInitializeRequest;
import com.ykskocluk.demo.config.IyzicoProperties;
import com.ykskocluk.demo.exception.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Real Iyzico Sandbox Client. Active only when {@code payments.iyzico.enabled=true}.
 * Communicates with the Iyzico sandbox API to initialize checkout forms.
 */
@Component
@ConditionalOnProperty(name = "payments.iyzico.enabled", havingValue = "true")
public class RealIyzicoClient implements IyzicoClient {

    private static final Logger log = LoggerFactory.getLogger(RealIyzicoClient.class);

    private final IyzicoProperties properties;
    private final Options options;

    public RealIyzicoClient(IyzicoProperties properties) {
        this.properties = properties;
        
        // Fast-fail validation on application start if enabled but properties are missing/blank
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Iyzico API Key is required when iyzico is enabled");
        }
        if (properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new IllegalStateException("Iyzico Secret Key is required when iyzico is enabled");
        }
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("Iyzico Base URL is required when iyzico is enabled");
        }

        this.options = new Options();
        this.options.setApiKey(properties.apiKey());
        this.options.setSecretKey(properties.secretKey());
        this.options.setBaseUrl(properties.baseUrl());
    }

    @Override
    public CheckoutResult initializeCheckout(Long subscriptionId, Long paymentId, BigDecimal amount, String idempotencyKey) {
        log.info("[RealIyzicoClient] Initializing checkout form for paymentId={}", paymentId);

        CreateCheckoutFormInitializeRequest request = new CreateCheckoutFormInitializeRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(paymentId.toString());
        request.setPrice(amount);
        request.setPaidPrice(amount);
        request.setCurrency(Currency.TRY.name());
        request.setBasketId("basket-" + subscriptionId);
        request.setPaymentGroup(PaymentGroup.PRODUCT.name());
        
        // Use the configured callback URL
        String callbackUrl = properties.callbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            // Fallback if callbackUrl not set
            callbackUrl = "http://localhost:8080/api/v1/payments/iyzico/webhook";
        }
        request.setCallbackUrl(callbackUrl);

        // Map buyer details. Since checkout API doesn't pass user details directly to client,
        // and it is sandbox client, we use placeholder details.
        Buyer buyer = new Buyer();
        buyer.setId("buyer-" + subscriptionId);
        buyer.setName("John");
        buyer.setSurname("Doe");
        buyer.setEmail("john.doe@example.com");
        buyer.setGsmNumber("+905555555555");
        buyer.setIdentityNumber("74300864791"); // Fake but valid TCKN
        buyer.setRegistrationAddress("Uskudar");
        buyer.setCity("Istanbul");
        buyer.setCountry("Turkey");
        request.setBuyer(buyer);

        // Address details
        Address address = new Address();
        address.setContactName("John Doe");
        address.setCity("Istanbul");
        address.setCountry("Turkey");
        address.setAddress("Uskudar");
        request.setBillingAddress(address);
        request.setShippingAddress(address);

        // Basket items details (Single virtual coaching item matching the price)
        List<BasketItem> basketItems = new ArrayList<>();
        BasketItem item = new BasketItem();
        item.setId("item-" + subscriptionId);
        item.setName("YKS Coaching Subscription");
        item.setCategory1("Education");
        item.setItemType(BasketItemType.VIRTUAL.name());
        item.setPrice(amount);
        basketItems.add(item);
        request.setBasketItems(basketItems);

        try {
            CheckoutFormInitialize response = CheckoutFormInitialize.create(request, options);
            if (response == null) {
                throw new PaymentProviderException("Iyzico returned no checkout response");
            }
            if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
                log.error("[RealIyzicoClient] Checkout initialization rejected; status={}, errorCode={}",
                        response.getStatus(), response.getErrorCode());
                throw new PaymentProviderException("Iyzico rejected checkout initialization");
            }

            log.info("[RealIyzicoClient] Checkout form initialized successfully for paymentId={}", paymentId);
            return new CheckoutResult(response.getToken(), response.getPaymentPageUrl());
        } catch (PaymentProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RealIyzicoClient] Checkout call failed; paymentId={}, exceptionType={}",
                    paymentId, e.getClass().getName());
            throw new PaymentProviderException("Iyzico checkout call failed", e);
        }
    }

    @Override
    public ChargeResult charge(String savedCardToken, BigDecimal amount, String idempotencyKey) {
        // Saved-card charging is not implemented by this adapter. Fail closed so a production
        // renewal can never be marked paid without an acknowledged provider transaction.
        throw new PaymentProviderException("Iyzico recurring charge is not implemented");
    }

    @Override
    public RefundResult refund(String providerReference, BigDecimal amount, String idempotencyKey) {
        log.info("[RealIyzicoClient] Requesting provider refund");

        // TODO: In a production-grade integration, iyzico requires paymentTransactionId for refunds.
        // If providerReference stored is the overall paymentId instead of the item's paymentTransactionId,
        // we would need to fetch the payment details from Iyzico first to resolve it, or ensure
        // paymentTransactionId is recorded during the success webhook flow.
        // For now, we attempt to call the iyzico refund endpoint directly assuming providerReference is the transaction ID.
        
        CreateRefundRequest request = new CreateRefundRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(idempotencyKey);
        request.setPaymentTransactionId(providerReference);
        request.setPrice(amount);
        request.setCurrency(Currency.TRY.name());
        request.setIp("127.0.0.1"); // Dummy IP required by Iyzico API

        try {
            Refund refundResponse = Refund.create(request, options);
            if (Status.SUCCESS.getValue().equals(refundResponse.getStatus())) {
                String ref = refundResponse.getPaymentTransactionId() != null ? refundResponse.getPaymentTransactionId() : refundResponse.getPaymentId();
                if (ref == null || ref.isBlank()) {
                    throw new PaymentProviderException("Iyzico returned an incomplete refund response");
                }
                log.info("[RealIyzicoClient] Refund completed successfully");
                return new RefundResult(true, ref, null, null);
            } else {
                log.error("[RealIyzicoClient] Refund rejected; status={}, errorCode={}",
                        refundResponse.getStatus(), refundResponse.getErrorCode());
                return new RefundResult(false, null, refundResponse.getErrorCode(), refundResponse.getErrorMessage());
            }
        } catch (PaymentProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RealIyzicoClient] Refund call failed; exceptionType={}", e.getClass().getName());
            return new RefundResult(false, null, "PROVIDER_CALL_FAILED", null);
        }
    }
}
