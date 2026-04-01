package egs.transactions_service.config;

import egs.transactions_service.blockchain.BlockchainProvider;
import egs.transactions_service.blockchain.MockBlockchainProvider;
import egs.transactions_service.blockchain.RealBlockchainProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "blockchain")
@Data
@Slf4j
public class BlockchainConfig {

    private String provider = "mock";  // Default: mock
    private Node node;
    private Contract contract;

    @PostConstruct
    public void init() {
        log.warn("====== BLOCKCHAIN CONFIG LOADED ======");
        log.warn("Provider: {}", provider);
        log.warn("Node URL: {}", node != null ? node.url : "NULL");
        log.warn("Chain ID: {}", node != null ? node.chainId : "NULL");
        log.warn("======================================");
    }

    @Data
    public static class Node {
        private String url;
        private long chainId;
    }

    @Data
    public static class Contract {
        private String address;
        private int decimals;
    }

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(node.getUrl()));
    }

    @Bean
    public BlockchainProvider blockchainProvider(Web3j web3j) {
        log.warn("Creating BlockchainProvider with provider={}", provider);
        if ("mock".equalsIgnoreCase(provider)) {
            log.warn(">>> Creating MockBlockchainProvider");
            return new MockBlockchainProvider();
        } else {
            log.warn(">>> Creating RealBlockchainProvider");
            return new RealBlockchainProvider(web3j, this);
        }
    }
}
