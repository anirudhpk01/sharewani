package com.sharewani.rentalportal.dto;

import com.sharewani.rentalportal.model.enums.RentalStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalDto {
    private Long id;
    
    @NotNull(message = "Start date is required")
    @Future(message = "Start date must be in the future")
    private Date startDate;
    
    @NotNull(message = "End date is required")
    @Future(message = "End date must be in the future")
    private Date endDate;
    
    private RentalStatus status;
    
    @NotNull(message = "Item ID is required")
    private Long itemId;
    
    @NotNull(message = "Tenant ID is required")
    private Long tenantId;
    
    private BigDecimal amount;
}