package egs.transactions_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Maps Keycloak user (sub claim) to a blockchain wallet address.
 * Used to automatically assign/retrieve a wallet when a user logs in.
 */
@Entity
@Table(name = "user_wallets", uniqueConstraints = @UniqueConstraint(columnNames = "keycloak_sub"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWallet {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true, length = 36)
    private String keycloakSub; // from JWT "sub" claim
    
    @Column(nullable = false, length = 42)
    private String walletAddress; // 0x...
    
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        if (this.walletAddress != null) {
            this.walletAddress = this.walletAddress.toLowerCase();
        }
    }
}
