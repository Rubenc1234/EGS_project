package egs.transactions_service.repository;

import egs.transactions_service.entity.TransactionRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRefundRepository extends JpaRepository<TransactionRefund, String> {
    
    /**
     * Find refunds for a specific transaction
     */
    List<TransactionRefund> findByTransactionId(String transactionId);
    
    /**
     * Find pending refunds for a wallet
     */
    List<TransactionRefund> findByToWalletAndStatus(String toWallet, TransactionRefund.RefundStatus status);
    
    /**
     * Check if a refund already exists for a transaction
     */
    Optional<TransactionRefund> findFirstByTransactionIdAndStatus(String transactionId, TransactionRefund.RefundStatus status);
}
