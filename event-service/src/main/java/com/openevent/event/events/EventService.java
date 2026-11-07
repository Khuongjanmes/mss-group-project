package com.openevent.event.events;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.ApiException;
import com.openevent.common.web.PageResponse;
import com.openevent.event.category.CategoryService;
import com.openevent.event.events.dto.EventRequest;
import com.openevent.event.events.dto.EventResponse;
import com.openevent.event.events.dto.EventSummaryResponse;
import com.openevent.event.venue.VenueService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final String ADMIN = "ADMIN";

    private final EventRepository events;
    private final CategoryService categories;
    private final VenueService venues;
    private final ApplicationEventPublisher publisher;

    /** Khách chỉ thấy sự kiện PUBLISHED, sắp diễn ra trước. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchPublished(EventSearch search, int page, int size) {
        EventSearch published = new EventSearch(search.keyword(), search.category(), search.city(), search.from(),
                search.to(), EventStatus.PUBLISHED, null);
        return search(published, page, size, Sort.by(Sort.Order.asc("startTime"), Sort.Order.asc("id")));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchMine(UUID organizerId, EventStatus status, int page, int size) {
        return search(new EventSearch(null, null, null, null, null, status, organizerId), page, size, newestFirst());
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchForAdmin(EventSearch search, int page, int size) {
        return search(search, page, size, newestFirst());
    }

    /** Sự kiện chưa công bố chỉ chủ sự kiện và Admin xem được; người khác nhận 404 để không lộ sự tồn tại. */
    @Transactional(readOnly = true)
    public EventResponse get(UUID id, AuthenticatedUser caller) {
        Event event = require(id);
        boolean allowed = event.getStatus().isPublic()
                || caller != null && (event.isOwnedBy(caller.id()) || caller.hasRole(ADMIN));
        if (!allowed) {
            throw notFound();
        }
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse create(UUID organizerId, EventRequest request) {
        Event event = Event.draft(organizerId);
        applyDetails(event, request);
        return EventResponse.from(events.save(event));
    }

    @Transactional
    public EventResponse update(UUID callerId, UUID id, EventRequest request) {
        Event event = requireOwned(callerId, id);
        applyDetails(event, request);
        return EventResponse.from(event);
    }

    @Transactional
    public void delete(UUID callerId, UUID id) {
        Event event = requireOwned(callerId, id);
        event.requireEditable();
        events.delete(event);
    }

    @Transactional
    public EventResponse submit(UUID callerId, UUID id) {
        Event event = requireOwned(callerId, id);
        event.submit(Instant.now());
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse approve(UUID id) {
        Event event = require(id);
        event.approve(Instant.now());
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse reject(UUID id, String reason) {
        Event event = require(id);
        event.reject(reason.trim());
        return EventResponse.from(event);
    }

    /** KB-5: Ticket và Order nhận EventCancelled để hủy vé, hủy đơn và yêu cầu hoàn tiền. */
    @Transactional
    public EventResponse cancel(AuthenticatedUser caller, UUID id) {
        Event event = require(id);
        if (!caller.hasRole(ADMIN) && !event.isOwnedBy(caller.id())) {
            throw notOwner();
        }
        Instant now = Instant.now();
        event.cancel(now);
        publisher.publishEvent(new EventCancelled(UUID.randomUUID(), now, event.getId()));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventSummaryResponse summary(UUID id) {
        return EventSummaryResponse.from(require(id));
    }

    private PageResponse<EventResponse> search(EventSearch search, int page, int size, Sort sort) {
        return PageResponse.from(events.findAll(search.toSpecification(), PageRequest.of(page, size, sort))
                .map(EventResponse::from));
    }

    private void applyDetails(Event event, EventRequest request) {
        event.updateDetails(request.title().trim(), request.description(), request.bannerUrl(),
                categories.require(request.categoryId()), venues.require(request.venueId()),
                request.startTime(), request.endTime(), Instant.now());
    }

    /** BR-06: Organizer chỉ thao tác trên sự kiện của mình. */
    private Event requireOwned(UUID callerId, UUID id) {
        Event event = require(id);
        if (!event.isOwnedBy(callerId)) {
            throw notOwner();
        }
        return event;
    }

    private Event require(UUID id) {
        return events.findById(id).orElseThrow(EventService::notFound);
    }

    private static Sort newestFirst() {
        return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Không tìm thấy sự kiện");
    }

    private static ApiException notOwner() {
        return new ApiException(HttpStatus.FORBIDDEN, "NOT_EVENT_OWNER", "Bạn không phải người tổ chức sự kiện này");
    }
}
