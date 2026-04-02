package egs.transactions_service.service;

import egs.transactions_service.config.TransactionConfig;
import egs.transactions_service.dto.FeeDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FeeCalculationService
 */
@DisplayName("FeeCalculationService Tests")
class FeeCalculationServiceTest {

    private FeeCalculationService feeCalculationService;
    
    @Mock
    private TransactionConfig transactionConfig;
    
    private final String RECIPIENT = "0x86a9906e6bd2ef137d6d5339154611de7a41b178";
    private final BigDecimal FEE_PERCENTAGE = new BigDecimal("2.0");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(transactionConfig.getFeePercentage()).thenReturn(FEE_PERCENTAGE);
        when(transactionConfig.getFeeRecipient()).thenReturn(RECIPIENT);
        feeCalculationService = new FeeCalculationService(transactionConfig);
    }

    @Test
    @DisplayName("Should calculate fee correctly for normal amount")
    void testCalculateFeeNormalAmount() {
        // Given
        BigDecimal amount = new BigDecimal("100.00");
        
        // When
        FeeDetail feeDetail = feeCalculationService.calculateFee(amount);
        
        // Then
        assertNotNull(feeDetail);
        assertEquals(0, feeDetail.getGrossAmount().compareTo(new BigDecimal("100.00")));
        assertEquals(0, feeDetail.getFeeAmount().compareTo(new BigDecimal("2.00")));
        assertEquals(0, feeDetail.getNetAmount().compareTo(new BigDecimal("98.00")));
        assertEquals(0, feeDetail.getFeePercentage().compareTo(new BigDecimal("2.0")));
        assertEquals(RECIPIENT, feeDetail.getRecipientAddress());
    }

    @Test
    @DisplayName("Should calculate fee for small amounts")
    void testCalculateFeeSmallAmount() {
        // Given
        BigDecimal amount = new BigDecimal("0.04");
        
        // When
        FeeDetail feeDetail = feeCalculationService.calculateFee(amount);
        
        // Then
        assertNotNull(feeDetail);
        assertEquals(0, feeDetail.getGrossAmount().compareTo(new BigDecimal("0.04")));
        assertEquals(0, feeDetail.getFeeAmount().compareTo(new BigDecimal("0.0008")));
        assertEquals(0, feeDetail.getNetAmount().compareTo(new BigDecimal("0.0392")));
    }

    @Test
    @DisplayName("Should calculate fee for large amounts")
    void testCalculateFeeLargeAmount() {
        // Given
        BigDecimal amount = new BigDecimal("10000.50");
        
        // When
        FeeDetail feeDetail = feeCalculationService.calculateFee(amount);
        
        // Then
        assertNotNull(feeDetail);
        assertEquals(0, feeDetail.getGrossAmount().compareTo(new BigDecimal("10000.50")));
        assertEquals(0, feeDetail.getFeeAmount().compareTo(new BigDecimal("200.01")));
        assertEquals(0, feeDetail.getNetAmount().compareTo(new BigDecimal("9800.49")));
    }

    @Test
    @DisplayName("Should calculate fee for zero amount")
    void testCalculateFeeZeroAmount() {
        // Given
        BigDecimal amount = BigDecimal.ZERO;
        
        // When & Then - should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            feeCalculationService.calculateFee(amount);
        });
    }

    @ParameterizedTest
    @DisplayName("Should calculate fee for various percentages")
    @ValueSource(doubles = {0.5, 1.0, 2.0, 5.0, 10.0})
    void testCalculateFeeVariousPercentages(double percentage) {
        // Given
        BigDecimal feePercentage = new BigDecimal(percentage);
        when(transactionConfig.getFeePercentage()).thenReturn(feePercentage);
        feeCalculationService = new FeeCalculationService(transactionConfig);
        BigDecimal amount = new BigDecimal("100.00");
        
        // When
        FeeDetail feeDetail = feeCalculationService.calculateFee(amount);
        
        // Then
        BigDecimal expectedFee = amount.multiply(feePercentage).divide(new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP);
        assertEquals(0, feeDetail.getFeeAmount().compareTo(expectedFee));
    }

    @Test
    @DisplayName("Should calculate fee with high precision (8 decimals)")
    void testCalculateFeeHighPrecision() {
        // Given
        BigDecimal amount = new BigDecimal("1.123456789"); // 9 decimals
        
        // When
        FeeDetail feeDetail = feeCalculationService.calculateFee(amount);
        
        // Then
        assertNotNull(feeDetail);
        // Verify we get a valid result (fee is positive and less than gross)
        assertTrue(feeDetail.getFeeAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(feeDetail.getFeeAmount().compareTo(feeDetail.getGrossAmount()) < 0);
    }

    @Test
    @DisplayName("Should maintain mathematical consistency")
    void testFeeCalculationConsistency() {
        // Given
        BigDecimal amount = new BigDecimal("50.25");
        
        // When
        FeeDetail feeDetail = feeCalculationService.calculateFee(amount);
        
        // Then
        // gross = fee + net (using compareTo for precision)
        BigDecimal calculated = feeDetail.getFeeAmount().add(feeDetail.getNetAmount());
        assertEquals(0, feeDetail.getGrossAmount().compareTo(calculated));
    }

    @Test
    @DisplayName("Should provide correct recipient address")
    void testRecipientAddressCorrect() {
        // When
        FeeDetail feeDetail = feeCalculationService.calculateFee(new BigDecimal("100"));
        
        // Then
        assertEquals(RECIPIENT, feeDetail.getRecipientAddress());
    }

    @Test
    @DisplayName("Should provide summary string")
    void testFeeSummaryString() {
        // Given
        BigDecimal amount = new BigDecimal("100.00");
        
        // When
        FeeDetail feeDetail = feeCalculationService.calculateFee(amount);
        String summary = feeDetail.getSummary();
        
        // Then
        assertNotNull(summary);
        assertFalse(summary.isBlank());
        assertTrue(summary.contains("2"));  // Fee percentage
        assertTrue(summary.contains("100")); // Gross amount
    }
}
