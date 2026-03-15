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
public class TransactionRequestDTO {
    @JsonProperty("from_wallet")
    private String fromWallet;
    
    @JsonProperty("to_wallet")
    private String toWallet;
    
    private BigDecimal amount;
    
    private String asset;
}
