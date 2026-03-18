package egs.transactions_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "balance_audits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    @Column(name = "native_balance", precision = 38, scale = 18)
    private BigDecimal nativeBalance;

    @Column(name = "token_balance", precision = 38, scale = 18)
    private BigDecimal tokenBalance;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @PrePersist
    protected void onCreate() {
        if(this.checkedAt == null) {
            this.checkedAt = OffsetDateTime.now();
        }
    }
}
