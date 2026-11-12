package com.openevent.event.venue;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    Page<Venue> findByCityIgnoreCase(String city, Pageable pageable);
}
