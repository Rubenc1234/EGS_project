package egs.transactions_service.service;

import egs.transactions_service.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentComposerService {

    private final RestTemplate restTemplate;
    private final TransactionService transactionService;

    @Value("${app.payment-service-url}")
    private String paymentServiceUrl;

    @Value("${app.bank-wallet}")
    private String bankWallet;

    public Map<String, Object> getLoginUrl(String redirectUri) {
        log.info("Fetching login URL for redirect_uri: {}", redirectUri);
        String url = paymentServiceUrl + "/v1/pay/login?redirect_uri=" + redirectUri;
        return restTemplate.getForObject(url, Map.class);
    }

    public Map<String, Object> getSignupUrl(String redirectUri) {
        log.info("Fetching signup URL for redirect_uri: {}", redirectUri);
        String url = paymentServiceUrl + "/v1/pay/signup?redirect_uri=" + redirectUri;
        return restTemplate.getForObject(url, Map.class);
    }

    public TokenResponseDTO exchangeCode(PaymentCallbackRequestDTO callbackRequest) {
        log.info("Exchanging code for token");
        String url = paymentServiceUrl + "/v1/pay/callback";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PaymentCallbackRequestDTO> request = new HttpEntity<>(callbackRequest, headers);
        return restTemplate.postForObject(url, request, TokenResponseDTO.class);
    }

    public PaymentResponseDTO createPayment(PaymentRequestDTO paymentRequest, String authHeader) {
        log.info("Creating payment for user {} with amount {}", paymentRequest.getUserId(), paymentRequest.getAmount());

        String url = paymentServiceUrl + "/v1/payments";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
        HttpEntity<PaymentRequestDTO> request = new HttpEntity<>(paymentRequest, headers);

        return restTemplate.postForObject(url, request, PaymentResponseDTO.class);
    }

    public PaymentResponseDTO getPayment(String paymentId, String authHeader) {
        log.info("Fetching payment details for {}", paymentId);
        String url = paymentServiceUrl + "/v1/payments/" + paymentId;
        
        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.GET, request, PaymentResponseDTO.class).getBody();
    }

    public PaymentResponseDTO updatePayment(String paymentId, PaymentUpdateDTO update, String authHeader) {
        log.info("Updating payment {} with status {}", paymentId, update.getStatus());

        String url = paymentServiceUrl + "/v1/payments/" + paymentId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
        HttpEntity<PaymentUpdateDTO> request = new HttpEntity<>(update, headers);

        // Call payment service to update status
        PaymentResponseDTO response = restTemplate.patchForObject(url, request, PaymentResponseDTO.class);

        if (response != null && "concluded".equalsIgnoreCase(update.getStatus())) {
            log.info("Payment {} concluded, triggering transaction", paymentId);
            triggerTransactionForPayment(paymentId, authHeader);
        }

        return response;
    }

    private void triggerTransactionForPayment(String paymentId, String authHeader) {
        try {
            // Fetch full payment details
            PaymentResponseDTO payment = getPayment(paymentId, authHeader);

            // Use robust helper to get target wallet
            String targetWallet = payment != null ? payment.getTargetWallet() : null;
            
            if (payment == null || targetWallet == null || payment.getAmount() == null) {
                log.warn("Could not fetch payment details for transaction triggering: {} (wallet_id={}, amount={})", 
                    paymentId, targetWallet, payment != null ? payment.getAmount() : "null");
                return;
            }

            // Create transaction payload
            TransactionRequestDTO txRequest = TransactionRequestDTO.builder()
                    .fromWallet(bankWallet)
                    .toWallet(targetWallet)
                    .amount(payment.getAmount().toString())
                    .asset("EUR")
                    .idempotencyKey("payment-" + paymentId)
                    .build();

            log.info("Triggering transaction for payment {}: from={} to={} amount={}", 
                paymentId, bankWallet, targetWallet, payment.getAmount());
            
            transactionService.createTransaction(txRequest);
            log.info("Transaction triggered successfully for payment {}", paymentId);

        } catch (Exception e) {
            log.error("Failed to trigger transaction for payment {}: {}", paymentId, e.getMessage());
            // Best-effort as in original composer
        }
    }
}
