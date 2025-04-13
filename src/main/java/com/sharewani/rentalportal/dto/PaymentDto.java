package com.sharewani.rentalportal.dto;

import com.sharewani.rentalportal.model.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

// @Data
// @NoArgsConstructor
// @AllArgsConstructor
// @Builder
// public class PaymentDto {
//     private Long id;
    
//     @NotNull(message = "Amount is required")
//     @Positive(message = "Amount must be positive")
//     private BigDecimal amount;
    
//     private Date date;
    
//     private PaymentStatus status;
    
//     @NotNull(message = "Rental ID is required")
//     private Long rentalId;
// }


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDto {
    private Long rentalId;
    private BigDecimal amount;
    private String method; // "CARD" or "BANK"
}
