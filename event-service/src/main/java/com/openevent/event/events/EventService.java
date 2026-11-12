package com.openevent.event.events;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.ApiException;
import com.openevent.common.web.PageResponse;
import com.openevent.event.category.CategoryService;
import com.openevent.event.client.IdentityClient.UserContact;
import com.openevent.event.client.UserDirectory;
import com.openevent.event.events.dto.EventAccessResponse;
import com.openevent.event.events.dto.EventRequest;
import com.openevent.event.events.dto.EventResponse;
import com.openevent.event.events.dto.EventSummaryResponse;
import com.openevent.event.events.dto.StaffResponse;
import com.openevent.event.media.ImageFiles;
import com.openevent.event.media.ImageStorage;
import com.openevent.event.media.ImageStorage.UploadedImage;
import com.openevent.event.organization.Membership;
import com.openevent.event.organization.Organization;
import com.openevent.event.organization.OrganizationRepository;
import com.openevent.event.venue.VenueService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final String ADMIN = "ADMIN";

    private final EventRepository events;
    private final CategoryService categories;
    private final VenueService venues;
    private final OrganizationRepository organizations;
    private final UserDirectory users;
    private final ImageStorage images;
    private final ApplicationEventPublisher publisher;
    /** Cho các thao tác gọi Identity hoặc Cloudinary: không giữ kết nối DB trong lúc chờ mạng. */
    private final TransactionTemplate tx;

    /** Khách chỉ thấy sự kiện PUBLISHED, sắp diễn ra trước. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchPublished(EventSearch search, int page, int size) {
        EventSearch published = new EventSearch(search.keyword(), search.category(), search.city(), search.from(),
                search.to(), EventStatus.PUBLISHED, null, null);
        return search(published, page, size, Sort.by(Sort.Order.asc("startTime"), Sort.Order.asc("id")));
    }

    /** Sự kiện mình làm HOST. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchMine(UUID organizerId, EventStatus status, int page, int size) {
        return search(new EventSearch(null, null, null, null, null, status, organizerId, null), page, size,
                newestFirst());
    }

    /**
     * Trang check-in: sự kiện mình soát vé được, đã công bố hoặc vừa kết thúc trong ngày (COMPLETED vẫn check-in được
     * tới hết ngày kết thúc, BR-09).
     */
    @Transactional(readOnly = true)
    public List<EventResponse> checkInable(UUID userId) {
        return events.findCheckInable(userId, List.of(EventStatus.PUBLISHED, EventStatus.COMPLETED),
                Instant.now().minus(Duration.ofDays(1))).stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchOrganization(UUID callerId, UUID organizationId, EventStatus status,
            int page, int size) {
        requireOrganizationMember(callerId, organizationId);
        return search(new EventSearch(null, null, null, null, null, status, null, organizationId), page, size,
                newestFirst());
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchForAdmin(EventSearch search, int page, int size) {
        return search(search, page, size, newestFirst());
    }

    /** Sự kiện chưa công bố chỉ người quản lý, STAFF và Admin xem được; người khác nhận 404 để không lộ sự tồn tại. */
    @Transactional(readOnly = true)
    public EventResponse get(UUID id, AuthenticatedUser caller) {
        Event event = require(id);
        boolean allowed = event.getStatus().isPublic()
                || caller != null && (caller.hasRole(ADMIN) || canCheckIn(event, caller.id()));
        if (!allowed) {
            throw notFound();
        }
        return EventResponse.from(event);
    }

    /** Người tạo thành HOST; tạo cho tổ chức thì phải là OWNER, MANAGER, hoặc MEMBER được cấp quyền (BR-14). */
    @Transactional
    public EventResponse create(UUID organizerId, EventRequest request) {
        if (request.organizationId() != null
                && !requireOrganizationMember(organizerId, request.organizationId()).mayCreateEvents()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_CREATE_EVENT",
                    "Bạn chưa được cấp quyền tạo sự kiện cho tổ chức này");
        }
        Organization organization = request.organizationId() == null ? null
                : organizations.getReferenceById(request.organizationId());
        Event event = Event.draft(organizerId, organization);
        applyDetails(event, request);
        return EventResponse.from(events.save(event));
    }

    @Transactional
    public EventResponse update(UUID callerId, UUID id, EventRequest request) {
        Event event = requireManaged(callerId, id);
        applyDetails(event, request);
        return EventResponse.from(event);
    }

    @Transactional
    public void delete(UUID callerId, UUID id) {
        Event event = requireManaged(callerId, id);
        event.requireEditable();
        events.delete(event);
    }

    @Transactional
    public EventResponse submit(UUID callerId, UUID id) {
        Event event = requireManaged(callerId, id);
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
        Event event = caller.hasRole(ADMIN) ? require(id) : requireManaged(caller.id(), id);
        Instant now = Instant.now();
        event.cancel(now);
        publisher.publishEvent(new EventCancelled(UUID.randomUUID(), now, event.getId()));
        return EventResponse.from(event);
    }

    /** BR-14: HOST mới phải là thành viên tổ chức, còn hoạt động và không phải Admin; sự kiện cá nhân không đổi HOST. */
    public EventResponse assignHost(UUID callerId, UUID id, UUID hostId) {
        tx.executeWithoutResult(status -> requireHostChange(callerId, id, hostId));
        users.requireAssignable(hostId);
        return tx.execute(status -> {
            Event event = requireHostChange(callerId, id, hostId);
            event.assignHost(hostId);
            return EventResponse.from(event);
        });
    }

    public List<StaffResponse> staff(UUID callerId, UUID id) {
        Set<UUID> staff = tx.execute(status -> Set.copyOf(requireManaged(callerId, id).staff()));
        Map<UUID, UserContact> contacts = users.contacts(staff);
        return staff.stream().map(userId -> toStaff(userId, contacts.get(userId)))
                .sorted(Comparator.comparing(member -> member.fullName() == null ? "" : member.fullName()))
                .toList();
    }

    /** STAFF thêm bằng email; tài khoản nào còn hoạt động (trừ Admin) cũng làm STAFF được, kể cả người ngoài tổ chức. */
    public StaffResponse addStaff(UUID callerId, UUID id, String email) {
        tx.executeWithoutResult(status -> requireManaged(callerId, id).requireActive());
        UserContact user = users.findAssignable(email);
        tx.executeWithoutResult(status -> requireManaged(callerId, id).addStaff(user.id()));
        return toStaff(user.id(), user);
    }

    @Transactional
    public void removeStaff(UUID callerId, UUID id, UUID userId) {
        requireManaged(callerId, id).removeStaff(userId);
    }

    /**
     * Tải ảnh bìa lên kho ảnh, trả về đường dẫn; ảnh chỉ gắn vào sự kiện khi người dùng bấm lưu (cùng form sửa).
     * Chỉ người quản lý sự kiện, khi sự kiện còn sửa được.
     */
    public UploadedImage uploadImage(UUID callerId, UUID id, MultipartFile file) {
        tx.executeWithoutResult(status -> requireManaged(callerId, id).requireEditable());
        ImageFiles.validate(file);
        return images.upload(file, "openevent/events/" + id);
    }

    @Transactional(readOnly = true)
    public EventSummaryResponse summary(UUID id) {
        return EventSummaryResponse.from(require(id));
    }

    /** Ticket và Order hỏi quyền ở đây thay vì tự suy ra từ vai trò tổ chức (KB-3). */
    @Transactional(readOnly = true)
    public EventAccessResponse access(UUID id, UUID userId) {
        Event event = require(id);
        return new EventAccessResponse(canManage(event, userId), canCheckIn(event, userId));
    }

    private static StaffResponse toStaff(UUID userId, UserContact contact) {
        return new StaffResponse(userId, contact == null ? null : contact.email(),
                contact == null ? null : contact.fullName());
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

    /** BR-06: HOST, hoặc OWNER/MANAGER của tổ chức sở hữu sự kiện (MEMBER chỉ quản lý sự kiện mình làm HOST). */
    private boolean canManage(Event event, UUID userId) {
        return event.isOwnedBy(userId) || event.getOrganizationId() != null
                && organizations.findMembership(event.getOrganizationId(), userId)
                        .filter(membership -> membership.getRole().managesAllEvents()).isPresent();
    }

    /** BR-09: người quản lý hoặc STAFF. */
    private boolean canCheckIn(Event event, UUID userId) {
        return event.isStaff(userId) || canManage(event, userId);
    }

    private Event requireHostChange(UUID callerId, UUID id, UUID hostId) {
        Event event = requireManaged(callerId, id);
        event.requireActive();
        if (event.getOrganizationId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PERSONAL_EVENT", "Sự kiện cá nhân không đổi HOST được");
        }
        if (organizations.findMembership(event.getOrganizationId(), hostId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOST_NOT_MEMBER", "HOST mới phải là thành viên tổ chức");
        }
        return event;
    }

    private Event requireManaged(UUID callerId, UUID id) {
        Event event = require(id);
        if (!canManage(event, callerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_EVENT_OWNER", "Bạn không có quyền quản lý sự kiện này");
        }
        return event;
    }

    /** Người ngoài tổ chức nhận 404 như {@code OrganizationService}. */
    private Membership requireOrganizationMember(UUID callerId, UUID organizationId) {
        return organizations.findMembership(organizationId, callerId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Không tìm thấy tổ chức"));
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
}
