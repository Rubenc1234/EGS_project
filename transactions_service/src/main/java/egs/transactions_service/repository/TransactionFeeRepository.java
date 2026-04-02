package egs.transactions_service.repository;

import egs.transactions_service.entity.TransactionFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionFeeRepository extends JpaRepository<TransactionFee, String> {
    List<TransactionFee> findByTransactionId(String transactionId);
    
    /**
     * Find the first (and typically only) fee for a transaction
     */
    Optional<TransactionFee> findFirstByTransactionId(String transactionId);
}
