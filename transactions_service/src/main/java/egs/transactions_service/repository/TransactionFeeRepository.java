package egs.transactions_service.repository;

import egs.transactions_service.entity.TransactionFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionFeeRepository extends JpaRepository<TransactionFee, String> {
    List<TransactionFee> findByTransactionId(String transactionId);
}
