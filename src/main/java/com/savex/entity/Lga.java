package com.savex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lgas",
       uniqueConstraints = @UniqueConstraint(columnNames = {"state_id", "name"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id", nullable = false)
    private State state;

    @Column(nullable = false, length = 100)
    private String name;
}
