package com.sharewani.rentalportal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "items")
public class Item {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private BigDecimal dailyRate;
    
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;
    
    @Column(nullable = false)
    private boolean available;
    
    @Column
    private String imageUrl;
    
    @Transient
    private ItemAvailabilityState state;
    
    public void setState(ItemAvailabilityState state) {
        this.state = state;
    }
    
    public boolean handleRentalRequest() {
        if (state == null) {
            state = available ? new AvailableState() : new UnavailableState();
        }
        return state.handleRequest(this);
    }
    
    public void setAvailable(boolean available) {
        this.available = available;
        this.state = available ? new AvailableState() : new UnavailableState();
    }
}