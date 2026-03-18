package egs.transactions_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@ConfigurationProperties(prefix = "blockchain")
@Data
public class BlockchainConfig {

    private Node node;
    private Contract contract;

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
}
