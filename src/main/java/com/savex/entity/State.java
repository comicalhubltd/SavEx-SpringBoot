package com.savex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "states")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class State {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 3)
    private String code;

    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
