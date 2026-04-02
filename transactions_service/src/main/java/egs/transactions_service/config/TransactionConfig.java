package egs.transactions_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * TransactionConfig - Configuração de transações
 * 
 * Lê propriedades de application.yaml:
 * app:
 *   transaction:
 *     fee-percentage: 2.0
 *     fee-recipient: "0x..."
 */
@Component
@ConfigurationProperties(prefix = "app.transaction")
@Data
public class TransactionConfig {
    
    private BigDecimal feePercentage = BigDecimal.valueOf(2.0);  // 2% default
    private String feeRecipient = "0x86a9906e6bd2ef137d6d5339154611de7a41b178";  // Bank wallet default
}
