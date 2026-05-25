package egs.transactions_service.scheduler;

import egs.transactions_service.entity.Transaction;
import egs.transactions_service.repository.TransactionRepository;
import egs.transactions_service.service.TransactionWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;


import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionIndexer {

    private final TransactionRepository transactionRepository;
    private final Web3j web3j;
    private final TransactionWorker transactionWorker;

    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration BROADCASTED_TIMEOUT = Duration.ofMinutes(15);

    // Executes every 5 seconds
    @Scheduled(fixedDelay = 5000)
    public void checkBroadcastedTransactions() {
        // Search for transactions that are in BROADCASTED or PENDING state
        List<Transaction> pendingTransactions = transactionRepository.findAllByStatus(Transaction.TransactionStatus.BROADCASTED);
        pendingTransactions.addAll(transactionRepository.findAllByStatus(Transaction.TransactionStatus.PENDING));

        if (pendingTransactions.isEmpty()) return;

        log.info("Indexador: A verificar {} transações pendentes na blockchain...", pendingTransactions.size());

        for (Transaction tx : pendingTransactions) {
            try {
                if (isExpired(tx)) {
                    log.warn("Transação {} expirou no estado {}. A marcar como FAILED.", tx.getId(), tx.getStatus());
                    transactionWorker.finalizeFailedTransaction(tx.getId(), "Transaction expired while waiting for blockchain confirmation");
                    continue;
                }

                if (tx.getStatus() == Transaction.TransactionStatus.PENDING) {
                    log.debug("Transação {} ainda PENDING e dentro do prazo. A aguardar worker.", tx.getId());
                    continue;
                }

                // Ask the blockchain if the transaction has been included in a block
                EthGetTransactionReceipt response = web3j.ethGetTransactionReceipt(tx.getHash()).send();
                Optional<TransactionReceipt> receiptOptional = response.getTransactionReceipt();

                if (receiptOptional.isPresent()) {
                    TransactionReceipt receipt = receiptOptional.get();
                    processReceipt(tx, receipt);
                }
                // If there is no receipt, the transaction is still in the mempool
            } catch (Exception e) {
                log.error("Erro ao indexar transação {}: {}", tx.getHash(), e.getMessage());
            }
        }
    }

    protected void processReceipt(Transaction tx, TransactionReceipt receipt) {
        if ("0x1".equals(receipt.getStatus())) {
            // SUCCESS
            log.info("Transação {} CONFIRMADA no bloco {}", tx.getId(), receipt.getBlockNumber());
            transactionWorker.finalizeConfirmedTransaction(tx.getId(), tx.getHash());
        } else {
            // FAILURE
            log.warn("Transação {} FALHOU na execução do contrato", tx.getId());
            transactionWorker.finalizeFailedTransaction(tx.getId(), "Blockchain execution failed");
        }
    }

    private boolean isExpired(Transaction tx) {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        java.time.OffsetDateTime referenceTime = tx.getStatus() == Transaction.TransactionStatus.PENDING
            ? tx.getCreatedAt()
            : tx.getUpdatedAt() != null ? tx.getUpdatedAt() : tx.getCreatedAt();

        if (referenceTime == null) {
            return false;
        }

        Duration age = Duration.between(referenceTime, now);
        if (tx.getStatus() == Transaction.TransactionStatus.PENDING) {
            return age.compareTo(PENDING_TIMEOUT) > 0;
        }
        return age.compareTo(BROADCASTED_TIMEOUT) > 0;
    }
}
