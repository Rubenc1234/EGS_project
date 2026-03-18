package egs.transactions_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    public enum TransactionStatus {
        CREATED, PENDING, BROADCASTED, CONFIRMED, FAILED
    }

    public enum TransactionType {
        TRANSFER, REFUND
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Column(name = "from_wallet", nullable = false, length = 42)
    private String fromWallet;

    @Column(name = "to_wallet", nullable = false, length = 42)
    private String toWallet;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(nullable = false)
    private String asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.CREATED; // CREATED, PENDING, CONFIRMED, FAILED

    @Column(name = "tx_hash")
    private String hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionType type = TransactionType.TRANSFER; // TRANSFER, REFUND

    @Column(name = "linked_tx_id")
    private String linkedTxId;

    @Column(name = "is_refunded")
    @Builder.Default
    private boolean refunded = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
