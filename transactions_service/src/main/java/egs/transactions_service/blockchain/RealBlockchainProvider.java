package egs.transactions_service.blockchain;

import egs.transactions_service.config.BlockchainConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.NoOpProcessor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Real Blockchain Provider — Web3j + Ethereum Sepolia (Testnet)
 * 
 * Conecta realmente a blockchain via RPC.
 * 
 * Criada pelo BlockchainConfig (não é @Component)
 */
@Slf4j
@RequiredArgsConstructor
public class RealBlockchainProvider implements BlockchainProvider {

    private final Web3j web3j;
    private final BlockchainConfig blockchainConfig;

    @Override
    public BigDecimal getBalance(String address) {
        try {
            EthGetBalance balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            BigDecimal balanceInEth = Convert.fromWei(new BigDecimal(balance.getBalance()), Convert.Unit.ETHER);
            log.debug("   [REAL] getBalance({}) = {} ETH", address, balanceInEth);
            return balanceInEth;
        } catch (Exception e) {
            log.error("   [REAL] ❌ getBalance erro: {}", e.getMessage());
            throw new RuntimeException("Failed to get balance from blockchain", e);
        }
    }

    @Override
    public String sendTransaction(String fromAddress, String toAddress, BigDecimal amount, String privateKey) {
        try {
            log.info("   [REAL] 📤 Preparando transação: {} ETH de {} → {}", amount, fromAddress, toAddress);
            
            // 1. Load credentials from private key
            Credentials credentials = Credentials.create(privateKey);
            String credentialAddress = credentials.getAddress().toLowerCase();
            String normalizedFromAddress = fromAddress.toLowerCase();
            log.info("   [REAL]    Credential address: {}", credentialAddress);
            log.info("   [REAL]    From address: {}", normalizedFromAddress);
            
            if (!credentialAddress.equals(normalizedFromAddress)) {
                log.error("   [REAL]    ❌ ADDRESS MISMATCH! Credential: {} != From: {}", credentialAddress, normalizedFromAddress);
                throw new RuntimeException("Private key address (" + credentialAddress + ") does not match fromAddress (" + normalizedFromAddress + ")");
            }
            
            // 2. Get nonce
            BigInteger nonce = getNonce(fromAddress);
            log.info("   [REAL]    Nonce: {}", nonce);
            
            // 3. Get gas price
            BigInteger gasPrice = getGasPrice();
            log.info("   [REAL]    Gas Price: {} Wei (~{} Gwei)", gasPrice, gasPrice.divide(BigInteger.valueOf(1_000_000_000)));
            
            // 4. Setup gas limit (standard transfer = 21000 gas)
            BigInteger gasLimit = BigInteger.valueOf(21_000);
            log.info("   [REAL]    Gas Limit: {}", gasLimit);
            
            // 5. Convert amount to Wei
            BigInteger amountWei = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger();
            log.info("   [REAL]    Amount: {} ETH = {} Wei", amount, amountWei);
            
            // 6. Create RawTransaction
            RawTransaction transaction = RawTransaction.createEtherTransaction(
                nonce,
                gasPrice,
                gasLimit,
                toAddress,
                amountWei
            );
            
            log.info("   [REAL]    RawTransaction created");
            
            // 7. Sign transaction with chain ID (Sepolia = 11155111)
            byte[] signedMessage = TransactionEncoder.signMessage(transaction, 11155111L, credentials);
            String hexSignedMessage = Numeric.toHexString(signedMessage);
            log.info("   [REAL]    Transaction signed, hex length: {}", hexSignedMessage.length());
            
            // 8. Send raw transaction
            EthSendTransaction response = web3j.ethSendRawTransaction(hexSignedMessage).send();
            
            if (response.getError() != null) {
                log.error("   [REAL]    ❌ RPC Error: {}", response.getError().getMessage());
                throw new RuntimeException("RPC Error: " + response.getError().getMessage());
            }
            
            String txHash = response.getTransactionHash();
            if (txHash == null || txHash.isEmpty()) {
                log.error("   [REAL]    ❌ No tx hash returned!");
                throw new RuntimeException("No transaction hash returned from RPC");
            }
            
            log.info("   [REAL] ✅ Transaction sent! Hash: {}", txHash);
            return txHash;
            
        } catch (Exception e) {
            log.error("   [REAL] ❌ Error sending transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send transaction", e);
        }
    }

    @Override
    public Optional<TransactionReceipt> getTransactionReceipt(String txHash) {
        try {
            log.debug("   [REAL] getTransactionReceipt({})", txHash);
            
            // Query para transaction receipt usando web3j
            org.web3j.protocol.core.methods.response.EthGetTransactionReceipt response = 
                web3j.ethGetTransactionReceipt(txHash).send();
            
            if (response.getTransactionReceipt().isPresent()) {
                org.web3j.protocol.core.methods.response.TransactionReceipt web3jReceipt = 
                    response.getTransactionReceipt().get();
                
                log.info("   [REAL] ✅ Receipt FOUND!");
                log.info("      Block Number: {}", web3jReceipt.getBlockNumber());
                log.info("      Status: {}", web3jReceipt.isStatusOK() ? "SUCCESS" : "FAILED");
                
                // Convert web3j receipt to our DTO
                TransactionReceipt receipt = new TransactionReceipt(
                    web3jReceipt.getTransactionHash(),
                    web3jReceipt.getBlockNumber().toString(),
                    web3jReceipt.getBlockHash(),
                    web3jReceipt.getFrom(),
                    web3jReceipt.getTo(),
                    web3jReceipt.isStatusOK(),
                    web3jReceipt.getGasUsed().toString()
                );
                
                return Optional.of(receipt);
            } else {
                log.debug("   [REAL] ⏳ No receipt yet for {}", txHash);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("   [REAL] ❌ getTransactionReceipt erro: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public BigInteger getGasPrice() {
        try {
            EthGasPrice gasPrice = web3j.ethGasPrice().send();
            log.debug("   [REAL] getGasPrice() = {} Gwei", gasPrice.getGasPrice().divide(BigInteger.valueOf(1_000_000_000)));
            return gasPrice.getGasPrice();
        } catch (Exception e) {
            log.error("   [REAL] ❌ getGasPrice erro: {}", e.getMessage());
            throw new RuntimeException("Failed to get gas price", e);
        }
    }

    @Override
    public BigInteger getNonce(String address) {
        try {
            EthGetTransactionCount count = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send();
            log.debug("   [REAL] getNonce({}) = {}", address, count.getTransactionCount());
            return count.getTransactionCount();
        } catch (Exception e) {
            log.error("   [REAL] ❌ getNonce erro: {}", e.getMessage());
            throw new RuntimeException("Failed to get nonce", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            web3j.web3ClientVersion().send();
            return true;
        } catch (Exception e) {
            log.error("   [REAL] ❌ Blockchain não acessível");
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "RealBlockchain (Polygon Amoy RPC)";
    }
}
