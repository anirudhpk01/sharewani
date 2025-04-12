package com.sharewani.rentalportal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;




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
    
    // @ManyToOne
    // @JoinColumn(name = "owner_id", nullable = false)
    // private Owner owner;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonBackReference
    private Owner owner;
    
    @Column(nullable = false)
    private boolean available;
    
    @Column
    private String imageUrl;
    
    @Transient
    @JsonIgnore
    private ItemAvailabilityState state;

    public String getCurrentState() {
    if (state == null) {
        state = available ? new AvailableState() : new UnavailableState();
    }
    return state.getClass().getSimpleName().replace("State", "");
}
    
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