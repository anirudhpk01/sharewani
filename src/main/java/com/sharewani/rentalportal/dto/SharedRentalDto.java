package com.sharewani.rentalportal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedRentalDto {
    private Long id;
    
    @NotNull(message = "Item ID is required")
    private Long itemId;
    
    private List<Long> tenantIds;
    
    @NotNull(message = "Max share count is required")
    @Min(value = 2, message = "Max share count must be at least 2")
    private int maxShareCount;
    
    private String schedule;
}