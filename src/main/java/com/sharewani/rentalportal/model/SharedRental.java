package com.sharewani.rentalportal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "shared_rentals")
public class SharedRental {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    
    @ManyToMany
    @JoinTable(
        name = "shared_rental_tenants",
        joinColumns = @JoinColumn(name = "shared_rental_id"),
        inverseJoinColumns = @JoinColumn(name = "tenant_id")
    )
    private List<Tenant> tenants = new ArrayList<>();
    
    @Column(nullable = false)
    private int maxShareCount;
    
    // For simplicity, we'll use a string to represent the schedule
    // In a real application, this would be a more complex structure
    @Column(columnDefinition = "TEXT")
    private String schedule;
}