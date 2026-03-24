package egs.transactions_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDTO {
    private String deviceId;              // "device-1", "chrome-iphone", etc
    private String platform;              // "web", "ios", "android"
    private String endpoint;              // "https://push-service.com/xyz"
    private Map<String, String> keys;     // {"p256dh": "...", "auth": "..."}
    private Map<String, Object> metadata; // browser, os, app_version, etc
}
