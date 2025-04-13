package com.sharewani.rentalportal.model;

import com.sharewani.rentalportal.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.time.LocalDate;

// @Data
// @NoArgsConstructor
// @AllArgsConstructor
// @Builder
// @Entity
// @Table(name = "payments")
// public class Payment {
    
//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;
    
//     @Column(nullable = false)
//     private BigDecimal amount;
    
//     @Column(nullable = false)
//     @Temporal(TemporalType.TIMESTAMP)
//     private Date date;
    
//     @Enumerated(EnumType.STRING)
//     @Column(nullable = false)
//     private PaymentStatus status;
    
//     @OneToOne
//     @JoinColumn(name = "rental_id", nullable = false)
//     private Rental rental;
// }


@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private Rental rental;

    private BigDecimal amount;

    private String method; // CARD, BANK, etc.

    private LocalDate paymentDate;
}
