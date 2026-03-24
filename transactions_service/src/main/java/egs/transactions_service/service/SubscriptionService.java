package egs.transactions_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import egs.transactions_service.dto.SubscriptionDTO;
import egs.transactions_service.model.Subscription;
import egs.transactions_service.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notifications.base-url:http://localhost:8082}")
    private String notificationsBaseUrl;

    @Value("${NOTIFICATIONS_API_KEY:}")
    private String notificationsApiKey;

    /**
     * Register a subscription for a user.
     * Also proxies to Notifications Service to ensure it's stored there.
     */
    public void subscribe(String userId, SubscriptionDTO subscriptionDTO) {
        log.info("📌 Registering subscription for user {} device {}", userId, subscriptionDTO.getDeviceId());

        try {
            // 1. Get JWT token from Notifications Service using API_KEY
            String tokenUrl = notificationsBaseUrl + "/v1/auth/token";
            
            Map<String, Object> tokenRequest = new HashMap<>();
            tokenRequest.put("user_id", userId);
            
            HttpHeaders tokenHeaders = new HttpHeaders();
            tokenHeaders.setContentType(MediaType.APPLICATION_JSON);
            if (notificationsApiKey != null && !notificationsApiKey.trim().isEmpty()) {
                tokenHeaders.setBearerAuth(notificationsApiKey);
                log.info("🔐 Using API_KEY for token generation");
            } else {
                log.warn("⚠️  NOTIFICATIONS_API_KEY not configured!");
            }
            
            HttpEntity<Map<String, Object>> tokenRequestEntity = new HttpEntity<>(tokenRequest, tokenHeaders);
            var tokenResponse = restTemplate.postForEntity(tokenUrl, tokenRequestEntity, Map.class);
            
            if (tokenResponse.getStatusCode().is2xxSuccessful() && tokenResponse.getBody() != null) {
                String jwtToken = (String) tokenResponse.getBody().get("token");
                log.info("✅ JWT token obtained from Notifications Service");
                
                // 2. Register subscription using JWT token
                String subscribeUrl = notificationsBaseUrl + "/v1/events/subscribe";
                
                Map<String, Object> subscribePayload = new HashMap<>();
                subscribePayload.put("endpoint", subscriptionDTO.getEndpoint());
                subscribePayload.put("keys", subscriptionDTO.getKeys());
                
                HttpHeaders subscribeHeaders = new HttpHeaders();
                subscribeHeaders.setContentType(MediaType.APPLICATION_JSON);
                subscribeHeaders.setBearerAuth(jwtToken);
                
                HttpEntity<Map<String, Object>> subscribeRequest = new HttpEntity<>(subscribePayload, subscribeHeaders);
                var subscribeResponse = restTemplate.postForEntity(subscribeUrl, subscribeRequest, Map.class);
                
                log.info("✅ Subscription registered in Notifications Service: {}", subscribeResponse.getStatusCode());
            } else {
                throw new RuntimeException("Failed to obtain JWT token from Notifications Service");
            }

            // 3. Store locally in Transactions DB (for audit/retry)
            Subscription sub = Subscription.builder()
                    .userId(userId)
                    .deviceId(subscriptionDTO.getDeviceId())
                    .platform(subscriptionDTO.getPlatform())
                    .endpoint(subscriptionDTO.getEndpoint())
                    .keys(objectMapper.writeValueAsString(subscriptionDTO.getKeys()))
                    .metadata(objectMapper.writeValueAsString(subscriptionDTO.getMetadata()))
                    .build();
            
            subscriptionRepository.save(sub);
            log.info("✅ Subscription stored locally for user {}", userId);

        } catch (Exception e) {
            log.error("❌ Error registering subscription for user {}", userId, e);
            throw new RuntimeException("Failed to register subscription", e);
        }
    }

    /**
     * Unsubscribe a user from a device
     */
    public void unsubscribe(String userId, String deviceId) {
        log.info("🔌 Unsubscribing user {} from device {}", userId, deviceId);
        subscriptionRepository.deleteByUserIdAndDeviceId(userId, deviceId);
    }
}
