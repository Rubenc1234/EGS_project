package egs.transactions_service.service;

import egs.transactions_service.config.BlockchainConfig;
import egs.transactions_service.dto.*;
import egs.transactions_service.event.TransactionCreatedEvent;
import egs.transactions_service.entity.BalanceAudit;
import egs.transactions_service.entity.Wallet;
import egs.transactions_service.repository.BalanceAuditRepository;
import egs.transactions_service.repository.TransactionRepository;
import egs.transactions_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final Web3j web3j;
    private final BlockchainConfig blockchainConfig;
    private final WalletRepository walletRepository;
    private final BalanceAuditRepository balanceAuditRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public BalanceDTO getBalance(String walletId) {
        log.info("=== getBalance ENTRY === walletId={}", walletId);
        validateWalletFormat(walletId);
        log.info("=== Wallet format validated ===");

        String normalizedWalletId = walletId.toLowerCase();
        log.info("=== Normalized walletId={} ===", normalizedWalletId);
        
        // Check if wallet exists in database
        Wallet wallet = walletRepository.findById(normalizedWalletId)
            .orElseGet(() -> {
                log.info("=== Wallet not in DB, creating new entry: {} ===", normalizedWalletId);
                Wallet newWallet = new Wallet();
                newWallet.setAddress(normalizedWalletId);
                newWallet.setLastNativeBalance(BigDecimal.ZERO);
                newWallet.setLastTokenBalance(BigDecimal.ZERO);
                Wallet saved = walletRepository.save(newWallet);
                log.info("=== New wallet created and saved in DB ===");
                return saved;
            });
        
        log.info("=== Wallet found in DB, querying blockchain ===");
        try {
            // Native Balance (Wei)
            log.info("=== Querying native balance (MATIC) ===");
            EthGetBalance ethGetBalance = web3j.ethGetBalance(walletId, DefaultBlockParameterName.LATEST).send();
            BigInteger nativeBalanceWei = ethGetBalance.getBalance();
            BigDecimal nativeBalance = Convert.fromWei(new BigDecimal(nativeBalanceWei), Convert.Unit.ETHER);
            log.info("=== Native balance retrieved: {} MATIC ===", nativeBalance);

            // Token Balance (Euro)
            log.info("=== Querying token balance (EUR) ===");
            String contractAddress = blockchainConfig.getContract().getAddress();
            BigInteger tokenBalanceRaw = queryTokenBalance(walletId, contractAddress);
            int decimals = blockchainConfig.getContract().getDecimals();
            BigDecimal tokenBalance = new BigDecimal(tokenBalanceRaw).divide(BigDecimal.valueOf(10).pow(decimals), decimals, RoundingMode.HALF_UP);
            log.info("=== Token balance retrieved: {} EUR ===", tokenBalance);

            OffsetDateTime now = OffsetDateTime.now();

            // Caching: Update wallet with last known balances
            // IMPORTANT: Only update cache if blockchain has HIGHER balance (to preserve dev-added funds)
            log.info("=== Updating cache in DB ===");
            log.info("=== Current cache: native={}, token={} ===", wallet.getLastNativeBalance(), wallet.getLastTokenBalance());
            log.info("=== Blockchain: native={}, token={} ===", nativeBalance, tokenBalance);
            
            // For native balance: always update (MATIC comes from blockchain)
            wallet.setLastNativeBalance(nativeBalance);
            
            // For token balance: only update if blockchain shows MORE than cache
            // (prevents clearing dev-added funds that haven't hit blockchain yet)
            BigDecimal cachedTokenBalance = wallet.getLastTokenBalance() != null ? wallet.getLastTokenBalance() : BigDecimal.ZERO;
            if (tokenBalance.compareTo(cachedTokenBalance) > 0) {
                log.info("=== Blockchain balance higher than cache, updating: {} > {} ===", tokenBalance, cachedTokenBalance);
                wallet.setLastTokenBalance(tokenBalance);
            } else {
                log.info("=== Keeping cached balance (dev funds?), not downgrading: {} >= {} ===", cachedTokenBalance, tokenBalance);
                // Use cached balance, don't overwrite
            }
            
            wallet.setLastUpdatedAt(now);
            walletRepository.save(wallet);
            log.info("=== Cache updated successfully ===");

            // Auditing: Record this balance check
            log.info("=== Recording balance audit ===");
            BalanceAudit audit = BalanceAudit.builder()
                    .walletAddress(walletId)
                    .nativeBalance(nativeBalance)
                    .tokenBalance(tokenBalance)
                    .checkedAt(now)
                    .build();
            balanceAuditRepository.save(audit);
            log.info("=== Audit recorded ===");

            BalanceDTO result = BalanceDTO.builder()
                    .walletId(walletId)
                    .symbol("EUR")
                    .balance(wallet.getLastTokenBalance().toPlainString())  // Use CACHED balance
                    .nativeBalance(nativeBalance.toPlainString())
                    .nativeSymbol("MATIC")
                    .balanceInFiat(wallet.getLastTokenBalance())  // Use CACHED balance
                    .currency("EUR")
                    .updatedAt(now)
                    .build();
            
            log.info("=== getBalance EXIT === result={}", result);
            return result;

        } catch (Exception e) {
            log.error("=== getBalance ERROR === walletId={} error={}", walletId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Blockchain query failed", e);
        }
    }

    private void validateWalletFormat(String walletId) {
        if (walletId == null || !walletId.startsWith("0x") || walletId.length() != 42) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid wallet address: must start with 0x and have 42 characters");
        }
    }

    private BigInteger queryTokenBalance(String walletId, String contractAddress) throws Exception {
        Function function = new Function(
                "balanceOf",
                List.of(new Address(walletId)),
                List.of(new TypeReference<Uint256>() {})
        );
        return queryUint256(contractAddress, function);
    }

    private int queryTokenDecimals(String contractAddress) throws Exception {
        Function function = new Function(
                "decimals",
                Collections.emptyList(),
                List.of(new TypeReference<Uint256>() {})
        );
        BigInteger result = queryUint256(contractAddress, function);
        return result.intValue();
    }

    private BigInteger queryUint256(String contractAddress, Function function) throws Exception {
        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
        ).send();

        if (response.hasError()) {
            throw new RuntimeException("Web3j call error: " + response.getError().getMessage());
        }

        List<Type> values = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (values.isEmpty()) {
            return BigInteger.ZERO;
        }
        return (BigInteger) values.get(0).getValue();
    }

    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public TransactionResponseDTO createTransaction(TransactionRequestDTO request) {
        log.info("createTransaction called: from={} to={} amount={} asset={} idempotencyKey={}", request.getFromWallet(), request.getToWallet(), request.getAmount(), request.getAsset(), request.getIdempotencyKey());
        // Validate format
        validateWalletFormat(request.getFromWallet());
        validateWalletFormat(request.getToWallet());

        if (request.getFromWallet().equalsIgnoreCase(request.getToWallet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender and receiver wallets cannot be the same");
        }

        if (request.getAmount() == null || new BigDecimal(request.getAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }

        // Idempotency check
        if (request.getIdempotencyKey() != null) {
            Optional<egs.transactions_service.entity.Transaction> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        // Validation (balance via cache)
        Wallet fromWallet = walletRepository.findById(request.getFromWallet().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source wallet not registered"));

        BigDecimal requiredAmount = new BigDecimal(request.getAmount());
        
        if ("EUR".equals(request.getAsset())) {
            if (fromWallet.getLastTokenBalance() == null || fromWallet.getLastTokenBalance().compareTo(requiredAmount) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds in Euro cache");
            }
        }

        // Logging
        egs.transactions_service.entity.Transaction tx = egs.transactions_service.entity.Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString())
                .fromWallet(request.getFromWallet())
                .toWallet(request.getToWallet())
                .amount(new BigDecimal(request.getAmount()))
                .asset(request.getAsset())
                .status(egs.transactions_service.entity.Transaction.TransactionStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
        
        tx = transactionRepository.save(tx);
    log.info("Transaction {} saved with status {}", tx.getId(), tx.getStatus());

        eventPublisher.publishEvent(new TransactionCreatedEvent(tx.getId()));
    log.info("TransactionCreatedEvent published for tx {}", tx.getId());

        // Caching (Optimistic Update / Fund Locking)
        if ("EUR".equals(request.getAsset())) {
            fromWallet.setLastTokenBalance(fromWallet.getLastTokenBalance().subtract(new BigDecimal(request.getAmount())));
        } else if ("MATIC".equals(request.getAsset())) {
            fromWallet.setLastNativeBalance(fromWallet.getLastNativeBalance().subtract(new BigDecimal(request.getAmount())));
        }
        walletRepository.save(fromWallet);

        // Response
        return mapToResponse(tx);
    }

    private TransactionResponseDTO mapToResponse(egs.transactions_service.entity.Transaction tx) {
        return TransactionResponseDTO.builder()
                .txId(tx.getId())
                .hash(tx.getHash())
                .status(tx.getStatus().name())
                .estimatedFee("0.001")
                .createdAt(tx.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionListResponseDTO listTransactions(String walletId, String status, int limit, int offset) {
        // Validation and constraints
        if (limit > 100) {
            log.warn("Limit {} exceeded maximum allowed. Capping to 100.", limit);
            limit = 100;
        }
        if (limit <= 0) limit = 10;
        if (offset < 0) offset = 0;

        // Convert status string to enum if provided
        egs.transactions_service.entity.Transaction.TransactionStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = egs.transactions_service.entity.Transaction.TransactionStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status provided: {}. Ignoring status filter.", status);
            }
        }

        // Query DB (paginated and sorted)
        int pageNumber = offset / limit;
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(
                pageNumber, limit, org.springframework.data.domain.Sort.by("createdAt").descending());

        org.springframework.data.domain.Page<egs.transactions_service.entity.Transaction> page = 
                transactionRepository.findByWalletAndStatus(walletId, statusEnum, pageRequest);

        // 3. Mapping to DTO
        List<TransactionItemDTO> items = page.getContent().stream()
                .map(tx -> TransactionItemDTO.builder()
                        .txId(tx.getId())
                        .hash(tx.getHash())
                        .from(tx.getFromWallet())
                        .to(tx.getToWallet())
                        .amount(tx.getAmount().toPlainString())
                        .status(tx.getStatus().name())
                        .confirmedAt(tx.getUpdatedAt())
                        .build())
                .toList();

        // 4. Response with Metadata
        return TransactionListResponseDTO.builder()
                .items(items)
                .total((int) page.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public RefundResponseDTO refundTransaction(RefundRequestDTO request) {
        // Input validation and fetching the original transaction
        egs.transactions_service.entity.Transaction originalTx = transactionRepository.findById(request.getOriginalTxId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Original transaction not found"));

        if (egs.transactions_service.entity.Transaction.TransactionStatus.CONFIRMED != originalTx.getStatus()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CONFIRMED transactions can be refunded");
        }

        if (originalTx.isRefunded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction already refunded or refund in progress");
        }

        // Balance verification
        Wallet originalReceiver = walletRepository.findById(originalTx.getToWallet().toLowerCase())
                .orElseGet(() -> {
                    log.info("Carteira do recetor original não encontrada. A registar na base de dados: {}", originalTx.getToWallet());
                    Wallet newWallet = new Wallet();
                    newWallet.setAddress(originalTx.getToWallet().toLowerCase());
                    newWallet.setLastNativeBalance(BigDecimal.ZERO);
                    newWallet.setLastTokenBalance(BigDecimal.ZERO);
                    return walletRepository.save(newWallet);
                });

        log.info("{}", originalTx.getAmount());

        BigDecimal buffer = new BigDecimal("0.0001");
        if ("EUR".equals(originalTx.getAsset())) {
            if (originalReceiver.getLastTokenBalance() == null || originalReceiver.getLastTokenBalance().compareTo(originalTx.getAmount()) < 0) {
                log.info("Insufficient Funds");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds for refund in Euro cache");
            }
        } else if ("MATIC".equals(originalTx.getAsset())) {
            if (originalReceiver.getLastNativeBalance() == null || originalReceiver.getLastNativeBalance().compareTo(originalTx.getAmount().add(buffer)) < 0) {
                log.info("Insufficient Funds: {} available, {} required (including buffer)", originalReceiver.getLastNativeBalance(), originalTx.getAmount().add(buffer));
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds for refund in MATIC cache");
            }
        }

        // DB update
        OffsetDateTime now = OffsetDateTime.now();

        // Create refund transaction
        egs.transactions_service.entity.Transaction refundTx = egs.transactions_service.entity.Transaction.builder()
                .idempotencyKey("refund-" + originalTx.getId())
                .fromWallet(originalTx.getToWallet())
                .toWallet(originalTx.getFromWallet())
                .amount(originalTx.getAmount())
                .asset(originalTx.getAsset())
                .status(egs.transactions_service.entity.Transaction.TransactionStatus.PENDING)
                .type(egs.transactions_service.entity.Transaction.TransactionType.REFUND)
                .linkedTxId(originalTx.getId())
                .createdAt(now)
                .build();

        refundTx = transactionRepository.save(refundTx);

        // Update original transaction
        originalTx.setRefunded(true);
        originalTx.setUpdatedAt(now);
        transactionRepository.save(originalTx);

        // Update cache
        if ("EUR".equals(originalTx.getAsset())) {
            originalReceiver.setLastTokenBalance(originalReceiver.getLastTokenBalance().subtract(originalTx.getAmount()));
        } else if ("MATIC".equals(originalTx.getAsset())) {
            originalReceiver.setLastNativeBalance(originalReceiver.getLastNativeBalance().subtract(originalTx.getAmount()));
        }
        walletRepository.save(originalReceiver);

        log.info("Refund {} dropped into queue for original tx {}", refundTx.getId(), originalTx.getId());
        eventPublisher.publishEvent(new TransactionCreatedEvent(refundTx.getId()));        

        return RefundResponseDTO.builder()
                .refundTxId(refundTx.getId())
                .originalTxId(originalTx.getId())
                .status(refundTx.getStatus().name())
                .message("Refund initiated successfully.")
                .amountRefunded(refundTx.getAmount().toPlainString())
                .asset(refundTx.getAsset())
                .build();
    }
}
