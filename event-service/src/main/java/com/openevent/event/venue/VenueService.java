package com.openevent.event.venue;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.web.ApiException;
import com.openevent.common.web.PageResponse;
import com.openevent.event.events.EventRepository;
import com.openevent.event.venue.dto.VenueRequest;
import com.openevent.event.venue.dto.VenueResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venues;
    private final EventRepository events;

    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> search(String city, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("name", "id"));
        Page<Venue> result = city == null || city.isBlank()
                ? venues.findAll(pageRequest)
                : venues.findByCityIgnoreCase(city.trim(), pageRequest);
        return PageResponse.from(result.map(VenueResponse::from));
    }

    @Transactional(readOnly = true)
    public VenueResponse get(UUID id) {
        return VenueResponse.from(require(id));
    }

    @Transactional
    public VenueResponse create(VenueRequest request) {
        return VenueResponse.from(venues.save(Venue.create(request.name().trim(), request.address().trim(),
                request.city().trim(), request.capacity())));
    }

    @Transactional
    public VenueResponse update(UUID id, VenueRequest request) {
        Venue venue = require(id);
        venue.update(request.name().trim(), request.address().trim(), request.city().trim(), request.capacity());
        return VenueResponse.from(venue);
    }

    @Transactional
    public void delete(UUID id) {
        Venue venue = require(id);
        if (events.existsByVenueId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "VENUE_IN_USE", "Địa điểm đang được sự kiện sử dụng");
        }
        venues.delete(venue);
    }

    public Venue require(UUID id) {
        return venues.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "VENUE_NOT_FOUND", "Không tìm thấy địa điểm"));
    }
}
