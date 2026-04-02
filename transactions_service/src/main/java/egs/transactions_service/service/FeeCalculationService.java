package egs.transactions_service.service;

import egs.transactions_service.config.TransactionConfig;
import egs.transactions_service.dto.FeeDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FeeCalculationService - Calcula taxas de transação
 * 
 * Responsabilidades:
 * - Calcular taxa baseada em percentagem
 * - Retornar detalhes de taxa
 * - Validar valores
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {
    
    private final TransactionConfig transactionConfig;
    
    /**
     * Calcula a taxa para uma transação
     * 
     * @param grossAmount Valor original (sem taxa)
     * @return FeeDetail com detalhes do cálculo
     */
    public FeeDetail calculateFee(BigDecimal grossAmount) {
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount deve ser maior que zero");
        }
        
        BigDecimal feePercentage = transactionConfig.getFeePercentage();
        
        // Calcular taxa: amount * (percentage / 100)
        BigDecimal feeAmount = grossAmount
            .multiply(feePercentage)
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        
        // Calcular valor líquido: amount - fee
        BigDecimal netAmount = grossAmount.subtract(feeAmount);
        
        log.info("=== Fee Calculation ===");
        log.info("Gross Amount: {}", grossAmount);
        log.info("Fee Percentage: {}%", feePercentage);
        log.info("Fee Amount: {}", feeAmount);
        log.info("Net Amount: {}", netAmount);
        
        return FeeDetail.builder()
            .feeAmount(feeAmount)
            .feePercentage(feePercentage)
            .grossAmount(grossAmount)
            .netAmount(netAmount)
            .recipientAddress(transactionConfig.getFeeRecipient())
            .build();
    }
    
    /**
     * Valida se o valor é suficiente para cobrir a taxa
     */
    public boolean isAmountSufficientForFee(BigDecimal amount) {
        FeeDetail fee = calculateFee(amount);
        return fee.getNetAmount().compareTo(BigDecimal.ZERO) > 0;
    }
}
