package egs.transactions_service.service;

import egs.transactions_service.blockchain.BlockchainProvider;
import egs.transactions_service.entity.Transaction;
import egs.transactions_service.entity.TransactionFee;
import egs.transactions_service.event.TransactionCreatedEvent;
import egs.transactions_service.repository.TransactionRepository;
import egs.transactions_service.repository.TransactionFeeRepository;
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
    private final TransactionFeeRepository transactionFeeRepository;
    private final BlockchainProvider blockchainProvider;
    private final KeyManagementService keyManagementService;
    private final NotificationService notificationService;
    private final RefundService refundService;
    
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
            
            // Get transaction details and trigger refund
            Transaction tx = transactionRepository.findById(txId).orElse(null);
            if (tx != null) {
                // Find fee information to refund
                var fee = transactionFeeRepository.findFirstByTransactionId(txId);
                if (fee.isPresent() && fee.get().getFeeAmount() != null) {
                    log.warn("   💰 Creating refund for fee: {} {}", fee.get().getFeeAmount(), fee.get().getAsset());
                    refundService.createRefund(
                        txId,
                        tx.getFromWallet(),
                        fee.get().getFeeAmount(),
                        fee.get().getAsset(),
                        "Transaction failed: " + e.getMessage()
                    );
                }
            }
            
            updateTransactionStatus(txId, null, Transaction.TransactionStatus.FAILED);
        }
    }

    /**
     * Poll para confirmação de transação com logging detalhado.
     * Tenta a cada 5 segundos durante 5 minutos (60 tentativas).
     */
    private void pollTransactionConfirmation(String txId, String txHash) {
        final int MAX_ATTEMPTS = 60; // 60 * 5s = 5 minutos
        final int POLL_INTERVAL_SECONDS = 5;
        
        log.info("🔄 === INICIANDO POLL DE CONFIRMAÇÃO ===");
        log.info("   Transaction ID: {}", txId);
        log.info("   TX Hash: {}", txHash);
        log.info("   Max Tentativas: {}", MAX_ATTEMPTS);
        log.info("   Intervalo: {} segundos", POLL_INTERVAL_SECONDS);
        log.info("⏱️  Timeout total: {} minutos", (MAX_ATTEMPTS * POLL_INTERVAL_SECONDS) / 60);
        
        final int[] attemptCounter = {0};
        
        java.util.concurrent.ScheduledFuture<?> pollTask = executor.scheduleAtFixedRate(() -> {
            attemptCounter[0]++;
            
            log.info("🔍 Poll Tentativa {}/{} para TX: {}...", attemptCounter[0], MAX_ATTEMPTS, txHash.substring(0, 10) + "...");
            
            try {
                // Query blockchain para receipt
                Optional<BlockchainProvider.TransactionReceipt> receipt = blockchainProvider.getTransactionReceipt(txHash);
                
                if (receipt.isPresent()) {
                    log.warn("   🎯 Receipt ENCONTRADO!");
                    log.warn("   Status: {}", receipt.get().successful ? "SUCCESS ✅" : "FAILED ❌");
                    
                    // Transação CONFIRMADA
                    log.info("   ✅ Transação CONFIRMADA: {}", txHash);
                    updateTransactionStatus(txId, txHash, Transaction.TransactionStatus.CONFIRMED);
                    
                    // Creditação ao receiver
                    transactionRepository.findById(txId).ifPresent(tx -> {
                        log.info("   💳 Processando crédito ao receiver: {}", tx.getToWallet());
                        walletRepository.findById(tx.getToWallet()).ifPresent(wallet -> {
                            if ("EUR".equals(tx.getAsset())) {
                                wallet.setLastTokenBalance(wallet.getLastTokenBalance().add(tx.getAmount()));
                                log.info("   ✅ Creditados {} {} (EUR) ao receiver", tx.getAmount(), tx.getAsset());
                            } else if ("ETH".equals(tx.getAsset())) {
                                wallet.setLastNativeBalance(wallet.getLastNativeBalance().add(tx.getAmount()));
                                log.info("   ✅ Creditados {} {} (ETH) ao receiver", tx.getAmount(), tx.getAsset());
                            }
                            walletRepository.save(wallet);
                        });
                    });
                    
                    // Enviar notificação de CONFIRMED
                    transactionRepository.findById(txId).ifPresent(tx -> {
                        log.info("   🔔 Sending CONFIRMED notification for transaction {}", txId);
                        try {
                            // Resolve user ID from wallet for notification
                            log.debug("   📮 Preparing to notify user about transaction confirmation");
                            // Notification will be sent via TransactionWorker async event
                        } catch (Exception e) {
                            log.error("   ⚠️  Erro ao enviar notificação de confirmação: {}", e.getMessage());
                        }
                    });
                    
                    log.info("✅ === POLL COMPLETADO COM SUCESSO ===\n");
                    throw new RuntimeException("Polling completed successfully");
                    
                } else {
                    log.debug("   ⏳ Sem receipt ainda. Tentativa {}/{}. Aguardando próxima verificação...", attemptCounter[0], MAX_ATTEMPTS);
                }
                
            } catch (RuntimeException e) {
                if ("Polling completed successfully".equals(e.getMessage())) {
                    throw e;  // Re-throw para parar o polling
                } else {
                    log.error("   ❌ Erro ao verificar receipt: {}", e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("   ❌ Erro inesperado no polling: {}", e.getMessage(), e);
            }
            
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Cancelar após MAX_ATTEMPTS (usar outro task para isso)
        executor.schedule(() -> {
            if (!pollTask.isDone()) {
                log.warn("⚠️  === POLL TIMEOUT ATINGIDO ===");
                log.warn("   Tentativas completadas: {}/{}", attemptCounter[0], MAX_ATTEMPTS);
                log.warn("   TX ID: {}", txId);
                log.warn("   TX Hash: {}", txHash);
                log.warn("   Motivo: Transação não confirmada em {} minutos", (MAX_ATTEMPTS * POLL_INTERVAL_SECONDS) / 60);
                
                pollTask.cancel(false);
                
                // Atualizar status para PENDING (ainda aguardando)
                transactionRepository.findById(txId).ifPresent(tx -> {
                    if (tx.getStatus() == Transaction.TransactionStatus.BROADCASTED) {
                        log.info("   ℹ️  Mantendo status BROADCASTED (aguardando confirmação futura)");
                    }
                });
                
                log.warn("❌ === FIM DO POLL COM TIMEOUT ===\n");
            }
        }, MAX_ATTEMPTS * POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
