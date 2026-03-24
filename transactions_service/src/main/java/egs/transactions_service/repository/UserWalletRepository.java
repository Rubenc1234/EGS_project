package egs.transactions_service.repository;

import egs.transactions_service.entity.UserWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWalletRepository extends JpaRepository<UserWallet, String> {
    Optional<UserWallet> findByKeycloakSub(String keycloakSub);
    Optional<UserWallet> findByWalletAddress(String walletAddress);
}
