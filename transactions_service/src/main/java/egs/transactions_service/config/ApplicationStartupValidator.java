package egs.transactions_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates complex configurations when application starts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupValidator {

    @Value("${app.transaction.fee-percentage:2.0}")
    private double feePercentage;
    
    @Value("${app.transaction.fee-recipient}")
    private String feeRecipient;

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        log.warn("=== VALIDATING CONFIGURATION AT STARTUP ===");
        try {
            FeeConfigValidator.validateFeeConfiguration(feePercentage, feeRecipient);
            log.warn("=== ✅ ALL CONFIGURATIONS VALID ===");
        } catch (IllegalArgumentException e) {
            log.error("=== ❌ CONFIGURATION VALIDATION FAILED ===");
            log.error("Error: {}", e.getMessage());
            throw new RuntimeException("Application startup failed due to configuration error: " + e.getMessage(), e);
        }
    }
}
