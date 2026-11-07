package com.openevent.event.venue;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "venues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    private Integer capacity;

    public static Venue create(String name, String address, String city, Integer capacity) {
        Venue venue = new Venue();
        venue.update(name, address, city, capacity);
        return venue;
    }

    public void update(String name, String address, String city, Integer capacity) {
        this.name = name;
        this.address = address;
        this.city = city;
        this.capacity = capacity;
    }
}
