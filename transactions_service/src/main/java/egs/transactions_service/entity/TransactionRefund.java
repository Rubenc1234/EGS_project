package egs.transactions_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Stores refund information when a transaction fails and fees need to be returned
 */
@Entity
@Table(name = "transaction_refunds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRefund {
    
    @Id
    @Column(length = 255)
    private String id;
    
    @Column(nullable = false, length = 36)
    private String transactionId;  // FK to transactions
    
    @Column(nullable = false, length = 42)
    private String toWallet;  // Where the refund goes (user's wallet)
    
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal refundAmount;  // Fee amount to refund
    
    @Column(nullable = false, length = 10)
    private String asset;  // "EUR" or "ETH"
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;  // PENDING, COMPLETED, FAILED
    
    @Column(length = 66)
    private String refundTxHash;  // Hash of refund transaction on blockchain
    
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    
    @Column
    private OffsetDateTime completedAt;
    
    @Column(columnDefinition = "TEXT")
    private String reason;  // Reason for refund (e.g., "Transaction failed", "User cancelled")
    
    public enum RefundStatus {
        PENDING,      // Refund requested, waiting to be processed
        COMPLETED,    // Refund sent to user's wallet
        FAILED        // Refund attempt failed
    }
}
