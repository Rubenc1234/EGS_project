package egs.transactions_service.controller;

import egs.transactions_service.dto.*;
import egs.transactions_service.service.PaymentComposerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175", "http://app.pt", "http://payment.pt", "http://iam.pt", "http://transactions.pt", "http://notifications.pt", "http://keycloak.pt", "http://payment-keycloak.pt"}, allowCredentials = "true")
public class ComposerController {

    private final PaymentComposerService paymentComposerService;
    
    @org.springframework.beans.factory.annotation.Value("${app.internal-api-key}")
    private String internalApiKey;

    @GetMapping("/pay/login")
    public ResponseEntity<?> getLoginUrl(@RequestParam("redirect_uri") String redirectUri) {
        log.info("GET /v1/pay/login called");
        try {
            Map<String, Object> response = paymentComposerService.getLoginUrl(redirectUri);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching login URL: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "payment_service_unreachable"));
        }
    }

    @GetMapping("/pay/signup")
    public ResponseEntity<?> getSignupUrl(@RequestParam("redirect_uri") String redirectUri) {
        log.info("GET /v1/pay/signup called");
        try {
            Map<String, Object> response = paymentComposerService.getSignupUrl(redirectUri);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching signup URL: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "payment_service_unreachable"));
        }
    }

    @PostMapping("/pay/callback")
    public ResponseEntity<?> handleCallback(@RequestBody PaymentCallbackRequestDTO request) {
        log.info("POST /v1/pay/callback called");
        try {
            TokenResponseDTO response = paymentComposerService.exchangeCode(request);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Error from payment service auth: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error exchanging code: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "payment_service_unreachable"));
        }
    }

    @PostMapping("/payments")
    public ResponseEntity<?> createPayment(
            @RequestBody PaymentRequestDTO request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("POST /v1/payments called for user: {}", request.getUserId());
        try {
            PaymentResponseDTO response = paymentComposerService.createPayment(request, authHeader);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Error from payment service: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error creating payment: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body(Map.of(
                    "error", "payment_service_unreachable",
                    "detail", e.getMessage()
            ));
        }
    }

    @GetMapping("/payments/{payment_id}")
    public ResponseEntity<?> getPayment(
            @PathVariable("payment_id") String paymentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("GET /v1/payments/{} called", paymentId);
        try {
            PaymentResponseDTO response = paymentComposerService.getPayment(paymentId, authHeader);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Error from payment service for payment {}: {} - {}", paymentId, e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error fetching payment {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.status(502).body(Map.of(
                    "error", "payment_service_unreachable",
                    "detail", e.getMessage()
            ));
        }
    }

    @PatchMapping("/payments/{payment_id}")
    public ResponseEntity<?> updatePayment(
            @PathVariable("payment_id") String paymentId,
            @RequestBody PaymentUpdateDTO update,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Key", required = false) String internalKey) {
        log.info("PATCH /v1/payments/{} called with status: {}", paymentId, update.getStatus());
        
        if (update.getStatus() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status required"));
        }

        // Allow bypass if internal key is provided
        String activeAuth = authHeader;
        log.info("Checking auth: authHeader={}, internalKey={}, internalApiKey={}", 
            authHeader != null ? "present" : "null", internalKey, internalApiKey);
            
        if (activeAuth == null && internalApiKey != null && internalApiKey.equals(internalKey)) {
             // We don't have a user token, but we have the internal key.
             log.info("Bypassing Authorization check for payment update via internal key");
        } else if (activeAuth == null) {
             log.warn("Authorization failed: header missing and internal key mismatch");
             return ResponseEntity.status(401).body(Map.of("error", "Authorization header missing"));
        }

        try {
            PaymentResponseDTO response = paymentComposerService.updatePayment(paymentId, update, activeAuth);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Error from payment service for payment {}: {} - {}", paymentId, e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error updating payment {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.status(502).body(Map.of(
                    "error", "payment_service_unreachable",
                    "detail", e.getMessage()
            ));
        }
    }
}
