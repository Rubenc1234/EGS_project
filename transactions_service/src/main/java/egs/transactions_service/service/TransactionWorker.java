package egs.transactions_service.service;

import egs.transactions_service.blockchain.BlockchainProvider;
import egs.transactions_service.entity.Transaction;
import egs.transactions_service.event.TransactionCreatedEvent;
import egs.transactions_service.repository.TransactionRepository;
import egs.transactions_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Transaction Worker — Processamento Assincronamente
 * 
 * Agora usa BlockchainProvider (Strategy Pattern).
 * Supports: MockBlockchain (dev), RealBlockchain (prod)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionWorker {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final BlockchainProvider blockchainProvider;
    private final KeyManagementService keyManagementService;
    private final NotificationService notificationService;
    
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processNewTransaction(TransactionCreatedEvent event) {
        String txId = event.getTransactionId();
        log.info("🔄 Worker acordou! A processar transação: {} com BlockchainProvider: {}", 
                txId, blockchainProvider.getProviderName());

        try {
            // 1. Busca detalhes da transação
            Transaction tx = transactionRepository.findById(txId)
                    .orElseThrow(() -> new RuntimeException("Transação não encontrada: " + txId));

            if (tx.getStatus() != Transaction.TransactionStatus.PENDING) {
                log.warn("   Transação {} não está PENDING. Status: {}", txId, tx.getStatus());
                return;
            }

            // 2. Envia para blockchain (Mock ou Real)
            log.info("   📤 Enviando {} {} de {} para {}", 
                    tx.getAmount(), tx.getAsset(), tx.getFromWallet(), tx.getToWallet());
            
            String privateKey = keyManagementService.getPrivateKeyForWallet(tx.getFromWallet());
            String txHash = blockchainProvider.sendTransaction(
                tx.getFromWallet(),
                tx.getToWallet(),
                tx.getAmount(),
                privateKey
            );
            
            log.info("   ✅ Transação enviada! Hash: {}", txHash);
            updateTransactionStatus(txId, txHash, Transaction.TransactionStatus.BROADCASTED);
            
            // 3. Poll confirmação (com retry)
            pollTransactionConfirmation(txId, txHash);

        } catch (Exception e) {
            log.error("   ❌ Erro ao processar transação {}: {}", txId, e.getMessage(), e);
            updateTransactionStatus(txId, null, Transaction.TransactionStatus.FAILED);
        }
    }

    /**
     * Poll para confirmação de transação.
     * Tenta a cada 5 segundos durante 5 minutos.
     */
    private void pollTransactionConfirmation(String txId, String txHash) {
        final int MAX_ATTEMPTS = 60; // 60 * 5s = 5 minutos
        final int POLL_INTERVAL_SECONDS = 5;
        
        executor.scheduleAtFixedRate(() -> {
            Optional<BlockchainProvider.TransactionReceipt> receipt = blockchainProvider.getTransactionReceipt(txHash);
            
            if (receipt.isPresent()) {
                log.info("   ✅ Transação CONFIRMADA: {}", txHash);
                updateTransactionStatus(txId, txHash, Transaction.TransactionStatus.CONFIRMED);
                
                // Creditação ao receiver
                transactionRepository.findById(txId).ifPresent(tx -> {
                    walletRepository.findById(tx.getToWallet()).ifPresent(wallet -> {
                        if ("EUR".equals(tx.getAsset())) {
                            wallet.setLastTokenBalance(wallet.getLastTokenBalance().add(tx.getAmount()));
                        } else {
                            wallet.setLastNativeBalance(wallet.getLastNativeBalance().add(tx.getAmount()));
                        }
                        walletRepository.save(wallet);
                        log.info("   💰 Creditados {} {} ao receiver", tx.getAmount(), tx.getAsset());
                    });
                });
                
                // Stop polling
                throw new RuntimeException("Stop polling");
            } else {
                log.debug("   ⏳ Aguardando confirmação de {}", txHash);
            }
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Transactional
    protected void updateTransactionStatus(String txId, String hash, Transaction.TransactionStatus status) {
        transactionRepository.findById(txId).ifPresent(tx -> {
            log.info("   💾 Atualizando TX {} - Status: {} | Hash: {} → {}", txId, tx.getStatus(), tx.getHash(), hash);
            
            if (hash != null) {
                tx.setHash(hash);
                log.info("   ✏️ Hash definido para: {}", tx.getHash());
            }
            
            tx.setStatus(status);
            tx.setUpdatedAt(OffsetDateTime.now());
            
            Transaction saved = transactionRepository.save(tx);
            log.info("   ✅ TX salva no repo. Hash na BD agora: {}", saved.getHash());

            // Send notifications based on transaction status
            if (status == Transaction.TransactionStatus.BROADCASTED) {
                log.info("🔔 Sending BROADCASTED notifications for transaction {}", txId);
                // Notify sender that transaction was sent
                notificationService.notifyTransaction(
                    tx.getFromWallet(), 
                    "Transaction Sent", 
                    String.format("Your transaction of %s %s to %s has been sent to the blockchain.", tx.getAmount(), tx.getAsset(), tx.getToWallet()),
                    java.util.Map.of(
                        "transaction_id", txId,
                        "amount", tx.getAmount().toPlainString(),
                        "currency", tx.getAsset(),
                        "status", "broadcasted"
                    )
                );
            } else if (status == Transaction.TransactionStatus.CONFIRMED) {
                log.info("🔔 Sending CONFIRMED notifications for transaction {}", txId);
                // Notify sender
                notificationService.notifyTransactionCompleted(
                    tx.getFromWallet(), 
                    txId, 
                    tx.getAmount().toPlainString(), 
                    tx.getAsset()
                );
                // Notify receiver
                notificationService.notifyTransactionCreated(
                    tx.getToWallet(), 
                    txId, 
                    tx.getAmount().toPlainString(), 
                    tx.getAsset()
                );
            } else if (status == Transaction.TransactionStatus.FAILED) {
                log.info("🔔 Sending FAILED notifications for transaction {}", txId);
                // Notify sender about failure
                notificationService.notifyRefund(
                    tx.getFromWallet(), 
                    txId, 
                    tx.getAmount().toPlainString(), 
                    tx.getAsset(),
                    "Transaction failed"
                );
                // Refund the cache
                refundCache(tx);
            }
        });
    }

    private void refundCache(Transaction tx) {
        walletRepository.findById(tx.getFromWallet()).ifPresent(wallet -> {
            log.info("A devolver saldo à cache da carteira {} devido a falha...", tx.getFromWallet());
            if ("EUR".equals(tx.getAsset())) {
                wallet.setLastTokenBalance(wallet.getLastTokenBalance().add(tx.getAmount()));
            } else {
                wallet.setLastNativeBalance(wallet.getLastNativeBalance().add(tx.getAmount()));
            }
            walletRepository.save(wallet);
        });
    }
}
