package egs.transactions_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Validates fee configuration at application startup
 */
@Slf4j
@Configuration
public class FeeConfigValidator {

    public static void validateFeeConfiguration(double feePercentage, String recipientAddress) {
        log.info("=== Validating Fee Configuration ===");
        
        // Validate fee percentage
        if (feePercentage < 0 || feePercentage > 100) {
            throw new IllegalArgumentException(
                String.format("Fee percentage must be between 0 and 100, but got: %f", feePercentage)
            );
        }
        log.info("✓ Fee percentage valid: {}%", feePercentage);

        // Validate recipient address format (Ethereum address)
        if (recipientAddress == null || recipientAddress.isBlank()) {
            throw new IllegalArgumentException("Fee recipient address cannot be null or empty");
        }

        if (!isValidEthereumAddress(recipientAddress)) {
            throw new IllegalArgumentException(
                String.format("Fee recipient address is not a valid Ethereum address: %s", recipientAddress)
            );
        }
        log.info("✓ Fee recipient address valid: {}", recipientAddress);
        
        log.info("=== Fee Configuration is VALID ===");
    }

    /**
     * Validates if the given string is a valid Ethereum address
     * Valid format: 0x followed by 40 hexadecimal characters
     */
    public static boolean isValidEthereumAddress(String address) {
        // Check format: 0x + 40 hex chars
        return address != null && 
               address.matches("^0x[0-9a-fA-F]{40}$");
    }
}
