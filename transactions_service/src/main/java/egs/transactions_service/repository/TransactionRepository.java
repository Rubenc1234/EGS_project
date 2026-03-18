package egs.transactions_service.repository;

import egs.transactions_service.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;


import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    List<Transaction> findAllByStatus(Transaction.TransactionStatus status);


    @Query("SELECT t FROM Transaction t WHERE " +
           "(:walletId IS NULL OR t.fromWallet = :walletId OR t.toWallet = :walletId) AND " +
           "(:status IS NULL OR t.status = :status)")
    Page<Transaction> findByWalletAndStatus(
            @Param("walletId") String walletId,
            @Param("status") Transaction.TransactionStatus status,
            Pageable pageable);
}
