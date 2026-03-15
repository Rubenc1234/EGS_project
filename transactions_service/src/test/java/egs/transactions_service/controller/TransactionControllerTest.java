package egs.transactions_service.controller;

import egs.transactions_service.dto.*;
import egs.transactions_service.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    public void testGetBalance() throws Exception {
        String walletId = "0x123";
        BalanceDTO balanceDTO = BalanceDTO.builder()
                .walletId(walletId)
                .symbol("ETH")
                .balance("1.50000000")
                .balanceInFiat(new BigDecimal("3500.50"))
                .currency("BRL")
                .updatedAt(OffsetDateTime.now())
                .build();

        when(transactionService.getBalance(anyString())).thenReturn(balanceDTO);

        mockMvc.perform(get("/v1/transactions/{wallet_id}/balance", walletId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet_id").value(walletId))
                .andExpect(jsonPath("$.symbol").value("ETH"))
                .andExpect(jsonPath("$.balance").value("1.50000000"))
                .andExpect(jsonPath("$.balance_in_fiat").value(3500.50))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.updated_at").exists());
    }

    @Test
    public void testCreateTransaction() throws Exception {
        TransactionResponseDTO responseDTO = TransactionResponseDTO.builder()
                .txId("internal-uuid-001")
                .hash("0xabc123")
                .status("PENDING")
                .estimatedFee("0.0002")
                .createdAt(OffsetDateTime.now())
                .build();

        when(transactionService.createTransaction(any(TransactionRequestDTO.class))).thenReturn(responseDTO);

        String requestBody = """
                {
                  "from_wallet": "0x123",
                  "to_wallet": "0x456",
                  "amount": 1.5,
                  "asset": "ETH"
                }
                """;

        mockMvc.perform(post("/v1/transactions/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tx_id").value("internal-uuid-001"))
                .andExpect(jsonPath("$.hash").value("0xabc123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.estimated_fee").value("0.0002"))
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    public void testListTransactions() throws Exception {
        TransactionItemDTO item = TransactionItemDTO.builder()
                .txId("internal-uuid-001")
                .hash("0xabc123")
                .from("0x123")
                .to("0x456")
                .amount("0.5")
                .status("CONFIRMED")
                .confirmedAt(OffsetDateTime.now())
                .build();

        TransactionListResponseDTO responseDTO = TransactionListResponseDTO.builder()
                .items(List.of(item))
                .total(150)
                .build();

        when(transactionService.listTransactions(any(), any(), anyInt(), anyInt())).thenReturn(responseDTO);

        mockMvc.perform(get("/v1/transactions/")
                .param("wallet_id", "0x123")
                .param("status", "CONFIRMED")
                .param("limit", "10")
                .param("offset", "0")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tx_id").value("internal-uuid-001"))
                .andExpect(jsonPath("$.items[0].hash").value("0xabc123"))
                .andExpect(jsonPath("$.items[0].from").value("0x123"))
                .andExpect(jsonPath("$.items[0].to").value("0x456"))
                .andExpect(jsonPath("$.items[0].amount").value("0.5"))
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].confirmed_at").exists())
                .andExpect(jsonPath("$.total").value(150));
    }

    @Test
    public void testRefundTransaction() throws Exception {
        RefundResponseDTO responseDTO = RefundResponseDTO.builder()
                .refundTxId("internal-uuid-099")
                .originalTxId("internal-uuid-001")
                .status("INITIATED")
                .message("Refund transaction iniciated.")
                .build();

        when(transactionService.refundTransaction(any(RefundRequestDTO.class))).thenReturn(responseDTO);

        String requestBody = """
                {
                  "original_tx_id": "internal-uuid-001"
                }
                """;

        mockMvc.perform(post("/v1/transactions/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refund_tx_id").value("internal-uuid-099"))
                .andExpect(jsonPath("$.original_tx_id").value("internal-uuid-001"))
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.message").value("Refund transaction broadcasted to network."));
    }
}
