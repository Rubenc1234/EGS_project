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
    private String feeRecipient = "0xfb4341ead4862e01637bf6c06c6863562e06e465";  // Bank wallet default
}
