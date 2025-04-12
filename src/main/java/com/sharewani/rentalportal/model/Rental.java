package com.sharewani.rentalportal.model;

import com.sharewani.rentalportal.model.enums.RentalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.ManyToOne;
// import jakarta.persistence.OneToOne;
// import jakarta.persistence.Table;

// import jakarta.persistence.GeneratedValue;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "rentals")
public class Rental {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    @Temporal(TemporalType.DATE)
    private Date startDate;
    
    @Column(nullable = false)
    @Temporal(TemporalType.DATE)
    private Date endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RentalStatus status;
    
    // @ManyToOne
    // @JoinColumn(name = "item_id", nullable = false)
    // private Item item;
    
    // @ManyToOne
    // @JoinColumn(name = "tenant_id", nullable = false)
    // private Tenant tenant;

    @ManyToOne
@JoinColumn(name = "item_id", nullable = false)
@JsonManagedReference(value = "rental-item")
private Item item;

@ManyToOne
@JoinColumn(name = "tenant_id", nullable = false)
@JsonManagedReference(value = "rental-tenant")
private Tenant tenant;

    
    @Column(nullable = false)
    private BigDecimal amount;
}