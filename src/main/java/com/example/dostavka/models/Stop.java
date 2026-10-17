package com.example.dostavka.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "stops",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stop_route_sequence", columnNames = {"route_id", "sequence_number"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Stop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "route_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Route route;

    @Column(nullable = false)
    private String address;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;
}