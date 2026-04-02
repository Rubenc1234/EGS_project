package egs.transactions_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * TransactionFee - Auditoria de taxas cobradas em transações
 * 
 * Guardar detalhes de toda taxa aplicada (transparência + auditoria)
 */
@Entity
@Table(name = "transaction_fees")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFee {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, length = 36)
    private String transactionId;  // Referência à transação original
    
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal feeAmount;  // Ex: 0.02 ETH
    
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal feePercentage;  // Ex: 2.00 (%)
    
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal grossAmount;  // Valor original (antes de taxa)
    
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal netAmount;  // Valor após taxa
    
    @Column(nullable = false, length = 42)
    private String recipientAddress;  // Onde a taxa vai (bank wallet)
    
    @Column(nullable = false, length = 10)
    private String asset;  // "ETH" ou "EUR"
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
