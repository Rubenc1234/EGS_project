package egs.transactions_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @Column(length = 42)
    private String address;

    @Column(name = "last_native_balance", precision = 38, scale = 18)
    @Builder.Default
    private BigDecimal lastNativeBalance = BigDecimal.ZERO;

    @Column(name = "last_token_balance", precision = 38, scale = 18)
    @Builder.Default
    private BigDecimal lastTokenBalance = BigDecimal.ZERO;

    @Column(name = "last_updated_at")
    private OffsetDateTime lastUpdatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        if (this.address != null) {
            this.address = this.address.toLowerCase();
        }

        this.lastUpdatedAt = OffsetDateTime.now();
    }
}
