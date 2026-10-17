package com.example.dostavka.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "shipment_assignments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_assignment_shipment", columnNames = {"shipment_id"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shipment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stop_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Stop stop;

    @Column(nullable = false)
    private String status;

    @PrePersist
    protected void onCreate() {
        if (this.status == null || this.status.isBlank()) {
            this.status = "ASSIGNED";
        }
    }
}