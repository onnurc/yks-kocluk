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
import com.iyzipay.request.CreateCheckoutFormInitializeRequest;
import com.ykskocluk.demo.config.IyzicoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        log.info("[RealIyzicoClient] Initializing checkout form: subId={}, paymentId={}, amount={}, key={}",
                subscriptionId, paymentId, amount, idempotencyKey);

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
            if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
                log.error("[RealIyzicoClient] Failed to initialize checkout form. Status={}, ErrorCode={}, ErrorMessage={}",
                        response.getStatus(), response.getErrorCode(), response.getErrorMessage());
                throw new RuntimeException("Iyzico checkout form initialization failed: " + response.getErrorMessage());
            }

            log.info("[RealIyzicoClient] Checkout form initialized successfully. Token={}", response.getToken());
            return new CheckoutResult(response.getToken(), response.getPaymentPageUrl());
        } catch (Exception e) {
            log.error("[RealIyzicoClient] Exception during iyzico checkout initialization", e);
            throw e;
        }
    }

    @Override
    public ChargeResult charge(String savedCardToken, BigDecimal amount, String idempotencyKey) {
        // Recurring charge via saved card (stubbed in sandbox phase as card storage / recurrences are out of scope)
        String reference = "sandbox-stub-ref-" + UUID.randomUUID();
        log.info("[RealIyzicoClient] Stub charge for saved card token={} amount={} (key={}) -> success, ref={}",
                savedCardToken, amount, idempotencyKey, reference);
        return new ChargeResult(true, reference);
    }
}
