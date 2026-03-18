package egs.transactions_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponseDTO {
    @JsonProperty("refund_tx_id")
    private String refundTxId;

    @JsonProperty("original_tx_id")
    private String originalTxId;

    private String status;
    private String message;

    @JsonProperty("amount_refunded")
    private String amountRefunded;

    private String asset;
}

