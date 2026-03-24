package egs.transactions_service.controller;

import egs.transactions_service.dto.SubscriptionDTO;
import egs.transactions_service.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users/me")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Register a push notification subscription for the current user.
     * Frontend sends subscription payload from Service Worker with user ID.
     */
    @PostMapping("/subscription")
    public ResponseEntity<Map<String, String>> subscribe(
            @RequestBody SubscriptionDTO subscriptionDTO,
            @RequestHeader(value = "X-User-Id", required = true) String userId) {
        
        log.info("📱 POST /v1/users/me/subscription ENTRY === deviceId={} userId={}", subscriptionDTO.getDeviceId(), userId);
        
        // Register subscription
        subscriptionService.subscribe(userId, subscriptionDTO);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Subscription registered successfully");
        response.put("device_id", subscriptionDTO.getDeviceId());
        
        log.info("=== POST /v1/users/me/subscription SUCCESS ===");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Unsubscribe from push notifications
     */
    @DeleteMapping("/subscription/{deviceId}")
    public ResponseEntity<Map<String, String>> unsubscribe(
            @PathVariable String deviceId,
            @RequestHeader(value = "X-User-Id", required = true) String userId) {
        
        subscriptionService.unsubscribe(userId, deviceId);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Unsubscribed successfully");
        
        return ResponseEntity.ok(response);
    }
}
