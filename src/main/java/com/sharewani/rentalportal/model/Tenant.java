package com.sharewani.rentalportal.model;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.JoinColumn;
import com.fasterxml.jackson.annotation.JsonBackReference;


import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "tenants")
public class Tenant extends User {
    
    // @OneToMany(mappedBy = "tenant")
    // private List<Rental> rentals = new ArrayList<>();
    @OneToMany(mappedBy = "tenant")
@JsonBackReference(value = "rental-tenant")
private List<Rental> rentals;

}