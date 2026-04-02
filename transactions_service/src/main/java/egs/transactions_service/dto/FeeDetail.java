package egs.transactions_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * FeeDetail - Detalhes do cálculo de taxa
 * 
 * Retorna informações sobre taxa calculada para uma transação
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeDetail {
    
    private BigDecimal feeAmount;      // Ex: 0.02 ETH
    private BigDecimal feePercentage;  // Ex: 2.00 (%)
    private BigDecimal grossAmount;    // Valor original
    private BigDecimal netAmount;      // Valor após taxa
    private String recipientAddress;   // Onde a taxa vai
    
    /**
     * Resumo legível da taxa
     */
    public String getSummary() {
        return String.format(
            "Fee: %.8f (%.2f%%) | Gross: %.8f | Net: %.8f",
            feeAmount, feePercentage, grossAmount, netAmount
        );
    }
}
