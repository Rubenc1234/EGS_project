package egs.transactions_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDTO {
    @JsonProperty("user_id")
    private String userId;
    
    private BigDecimal amount;
    
    @JsonProperty("wallet_id")
    private String walletId;

    @JsonProperty("to_wallet")
    private String toWallet;

    @JsonProperty("redirect_url")
    private String redirectUrl;

    public String getTargetWallet() {
        return walletId != null ? walletId : toWallet;
    }
}
