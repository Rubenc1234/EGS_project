package egs.transactions_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RestTemplate restTemplate;

    @Value("${notifications.base-url:http://localhost:5003}")
    private String notificationsBaseUrl;

    @Value("${NOTIFICATIONS_API_KEY:}")
    private String notificationsApiKey;

    /**
     * Send a transaction notification to a user via the Notifications Service
     */
    public void notifyTransaction(String userId, String title, String message, Map<String, Object> metadata) {
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║ NOTIFICATION SERVICE - SENDING NOTIFICATION                    ║");
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║ User ID:             {}", String.format("%-47s║", userId));
        log.info("║ Title:               {}", String.format("%-47s║", title));
        log.info("║ Message:             {}", String.format("%-47s║", message));
        log.info("║ Notifications URL:   {}", String.format("%-47s║", notificationsBaseUrl));
        log.info("╚════════════════════════════════════════════════════════════════╝");
        
        // Check if API key is configured
        if (notificationsApiKey == null || notificationsApiKey.trim().isEmpty()) {
            log.warn("⚠️  NOTIFICATIONS_API_KEY not configured - skipping notification to user: {}", userId);
            return;
        }
        
        try {
            String url = notificationsBaseUrl + "/v1/events";
            log.info("📡 Target URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(notificationsApiKey);
            log.info("🔐 Auth header set with API Key (configured)");

            Map<String, Object> body = new HashMap<>();
            // The Notifications Service expects user_ids as an array
            java.util.List<String> userIdsList = new java.util.ArrayList<>();
            userIdsList.add(userId);
            body.put("user_ids", userIdsList);
            body.put("message", message);
            
            if (metadata != null) {
                log.info("📦 Adding metadata: {}", metadata);
                body.putAll(metadata);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            log.info("📤 Sending POST request to Notifications Service...");
            log.debug("📋 Request body: {}", body);
            
            var response = restTemplate.postForEntity(url, request, Map.class);
            
            log.info("✅ Response Status: {}", response.getStatusCode());
            log.info("📥 Response Body: {}", response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✨ ✅ Notification sent SUCCESSFULLY to user: {}", userId);
                log.info("   Title: '{}', Message: '{}'", title, message);
            } else {
                log.warn("⚠️  Notification delivery had non-success status: {} for user: {}", response.getStatusCode(), userId);
            }
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR sending notification to user: {}", userId, e);
            log.error("❌ Error Type: {}", e.getClass().getSimpleName());
            log.error("❌ Error Message: {}", e.getMessage());
            log.error("❌ Stack trace: ", e);
            // Don't throw exception - notification failure shouldn't block transaction
            log.warn("⚠️  Notification failed but transaction continues (non-blocking)");
        }
    }

    /**
     * Send a transaction notification with additional transaction details
     */
    public void notifyTransactionCreated(String userId, String transactionId, String amount, String currency) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("transaction_id", transactionId);
        metadata.put("amount", amount);
        metadata.put("currency", currency);

        String message = String.format("Transaction of %s %s created successfully", amount, currency);
        notifyTransaction(userId, "Transaction Created", message, metadata);
    }

    /**
     * Send a transaction completed notification
     */
    public void notifyTransactionCompleted(String userId, String transactionId, String amount, String currency) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("transaction_id", transactionId);
        metadata.put("amount", amount);
        metadata.put("currency", currency);
        metadata.put("status", "completed");

        String message = String.format("Transaction of %s %s completed", amount, currency);
        notifyTransaction(userId, "Transaction Completed", message, metadata);
    }

    /**
     * Send a refund notification
     */
    public void notifyRefund(String userId, String transactionId, String amount, String currency, String reason) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("transaction_id", transactionId);
        metadata.put("amount", amount);
        metadata.put("currency", currency);
        metadata.put("reason", reason);

        String message = String.format("Refund of %s %s processed. Reason: %s", amount, currency, reason);
        notifyTransaction(userId, "Refund Processed", message, metadata);
    }
}
