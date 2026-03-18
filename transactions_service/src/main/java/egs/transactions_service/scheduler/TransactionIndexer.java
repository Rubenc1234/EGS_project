package egs.transactions_service.scheduler;

import egs.transactions_service.entity.Transaction;
import egs.transactions_service.repository.TransactionRepository;
import egs.transactions_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionIndexer {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final Web3j web3j;

    // Executes every 5 seconds
    @Scheduled(fixedDelay = 5000)
    public void checkBroadcastedTransactions() {
        // Search for transactions that are in BROADCASTED state
        List<Transaction> pendingTransactions = transactionRepository.findAllByStatus(Transaction.TransactionStatus.BROADCASTED);

        if (pendingTransactions.isEmpty()) return;

        log.info("Indexador: A verificar {} transações pendentes na blockchain...", pendingTransactions.size());

        for (Transaction tx : pendingTransactions) {
            try {
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

    @Transactional
    protected void processReceipt(Transaction tx, TransactionReceipt receipt) {
        if ("0x1".equals(receipt.getStatus())) {
            // SUCCESS
            log.info("Transação {} CONFIRMADA no bloco {}", tx.getId(), receipt.getBlockNumber());
            tx.setStatus(Transaction.TransactionStatus.CONFIRMED);
        } else {
            // FAILURE
            log.warn("Transação {} FALHOU na execução do contrato", tx.getId());
            tx.setStatus(Transaction.TransactionStatus.FAILED);
            
            refundCache(tx);
        }

        tx.setUpdatedAt(OffsetDateTime.now());
        transactionRepository.save(tx);
    }

    private void refundCache(Transaction tx) {
        walletRepository.findById(tx.getFromWallet()).ifPresent(wallet -> {
            if ("EUR".equals(tx.getAsset())) {
                wallet.setLastTokenBalance(wallet.getLastTokenBalance().add(tx.getAmount()));
            } else {
                wallet.setLastNativeBalance(wallet.getLastNativeBalance().add(tx.getAmount()));
            }
            walletRepository.save(wallet);
            log.info("Saldo devolvido à cache da carteira {} devido a falha na rede.", tx.getFromWallet());
        });
    }
}