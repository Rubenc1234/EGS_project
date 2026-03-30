package egs.transactions_service.blockchain;

import egs.transactions_service.config.BlockchainConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Real Blockchain Provider — Web3j + Polygon Amoy (Testnet)
 * 
 * Conecta realmente a blockchain via RPC.
 * 
 * Ativa: spring.profiles.active=prod (ou default)
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "blockchain.provider", havingValue = "real", matchIfMissing = true)
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
            // TODO: Implementar completo com RawTransaction, signing, etc.
            // Por agora, apenas placeholder
            log.info("   [REAL] 📤 sendTransaction: {} EUR from {} to {}", amount, fromAddress, toAddress);
            throw new UnsupportedOperationException("RealBlockchainProvider.sendTransaction não implementado ainda");
        } catch (Exception e) {
            log.error("   [REAL] ❌ sendTransaction erro: {}", e.getMessage());
            throw new RuntimeException("Failed to send transaction", e);
        }
    }

    @Override
    public Optional<TransactionReceipt> getTransactionReceipt(String txHash) {
        try {
            // TODO: Implementar com web3j.ethGetTransactionReceipt()
            log.debug("   [REAL] getTransactionReceipt({})", txHash);
            return Optional.empty();
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
