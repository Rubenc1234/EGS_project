package egs.transactions_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import egs.transactions_service.repository.UserWalletRepository;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RestTemplate restTemplate;
    private final UserWalletRepository userWalletRepository;

    @Value("${notifications.base-url:http://localhost:8082}")
    private String notificationsBaseUrl;

    @Value("${NOTIFICATIONS_API_KEY:}")
    private String notificationsApiKey;

    /**
     * Send a transaction notification to a user via the Notifications Service.
     * If the userId is a wallet address, it will be resolved to the Keycloak sub.
     */
    public void notifyTransaction(String userId, String title, String message, Map<String, Object> metadata) {
        // Try to resolve wallet address to Keycloak sub
        String notificationUserId = resolveUserId(userId);
        
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║ NOTIFICATION SERVICE - SENDING NOTIFICATION                    ║");
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║ Original User ID:    {}", String.format("%-47s║", userId));
        log.info("║ Resolved User ID:    {}", String.format("%-47s║", notificationUserId));
        log.info("║ Title:               {}", String.format("%-47s║", title));
        log.info("║ Message:             {}", String.format("%-47s║", message));
        log.info("║ Notifications URL:   {}", String.format("%-47s║", notificationsBaseUrl));
        log.info("╚════════════════════════════════════════════════════════════════╝");
        
        // Check if API key is configured
        if (notificationsApiKey == null || notificationsApiKey.trim().isEmpty()) {
            log.warn("⚠️  NOTIFICATIONS_API_KEY not configured - skipping notification to user: {}", notificationUserId);
            return;
        }
        
        // If resolution failed, skip notification
        if (notificationUserId == null || notificationUserId.isBlank()) {
            log.warn("⚠️  Could not resolve notification user ID for wallet: {} - skipping", userId);
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
            userIdsList.add(notificationUserId);
            body.put("user_ids", userIdsList);
            body.put("title", title);
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
                log.info("✨ ✅ Notification sent SUCCESSFULLY to user: {}", notificationUserId);
                log.info("   Title: '{}', Message: '{}'", title, message);
            } else {
                log.warn("⚠️  Notification delivery had non-success status: {} for user: {}", response.getStatusCode(), notificationUserId);
            }
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR sending notification to user: {}", notificationUserId, e);
            log.error("❌ Error Type: {}", e.getClass().getSimpleName());
            log.error("❌ Error Message: {}", e.getMessage());
            log.error("❌ Stack trace: ", e);
            // Don't throw exception - notification failure shouldn't block transaction
            log.warn("⚠️  Notification failed but transaction continues (non-blocking)");
        }
    }
    
    /**
     * Resolve a wallet address to its Keycloak sub claim.
     * If the input is already a Keycloak sub (UUID), return as-is.
     */
    private String resolveUserId(String identifier) {
        // If it looks like a UUID, assume it's already a Keycloak sub
        if (identifier != null && identifier.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            log.info("🔍 Identifier looks like a Keycloak sub, using as-is: {}", identifier);
            return identifier;
        }
        
        // Try to find wallet in database and get associated keycloakSub
        if (identifier != null && identifier.toLowerCase().startsWith("0x")) {
            log.info("🔍 Resolving wallet address to Keycloak sub: {}", identifier);
            var wallet = userWalletRepository.findByWalletAddress(identifier);
            if (wallet.isPresent()) {
                String keycloakSub = wallet.get().getKeycloakSub();
                log.info("✅ Resolved wallet {} to Keycloak sub: {}", identifier, keycloakSub);
                return keycloakSub;
            } else {
                log.warn("⚠️  No wallet found for address: {}", identifier);
                return null;
            }
        }
        
        return identifier;
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
