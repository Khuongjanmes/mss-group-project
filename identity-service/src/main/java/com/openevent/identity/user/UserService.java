package com.openevent.identity.user;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.web.ApiException;
import com.openevent.common.web.PageResponse;
import com.openevent.identity.auth.AuthErrors;
import com.openevent.identity.auth.RefreshTokenService;
import com.openevent.identity.user.dto.UpdateProfileRequest;
import com.openevent.identity.user.dto.UserContactResponse;
import com.openevent.identity.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;

    @Transactional(readOnly = true)
    public UserResponse getCurrent(UUID callerId) {
        return UserResponse.from(requireActiveCaller(callerId));
    }

    @Transactional
    public UserResponse updateProfile(UUID callerId, UpdateProfileRequest request) {
        User user = requireActiveCaller(callerId);
        user.updateProfile(request.fullName().trim(), request.phone(), request.avatarUrl());
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(UUID callerId, String currentPassword, String newPassword) {
        User user = requireActiveCaller(callerId);
        if (user.getPasswordHash() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_LOCAL_PASSWORD",
                    "Tài khoản đăng nhập bằng Google không có mật khẩu");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Mật khẩu hiện tại không đúng");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAll(user.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UUID adminId, String keyword, UserStatus status, int page, int size) {
        requireActiveCaller(adminId);
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.from(users
                .findAll(UserSpecifications.keyword(keyword).and(UserSpecifications.status(status)), pageRequest)
                .map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID adminId, UUID id) {
        requireActiveCaller(adminId);
        return UserResponse.from(requireUser(id));
    }

    @Transactional
    public UserResponse lock(UUID adminId, UUID id) {
        requireActiveCaller(adminId);
        if (adminId.equals(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_LOCK_SELF", "Không thể tự khóa tài khoản của mình");
        }
        User user = requireUser(id);
        user.lock();
        refreshTokens.revokeAll(id);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse unlock(UUID adminId, UUID id) {
        requireActiveCaller(adminId);
        User user = requireUser(id);
        user.unlock();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse grantOrganizer(UUID adminId, UUID id) {
        requireActiveCaller(adminId);
        User user = requireUser(id);
        user.addRole(roles.findByName(RoleName.ORGANIZER).orElseThrow());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserContactResponse getContact(UUID id) {
        return UserContactResponse.from(requireUser(id));
    }

    @Transactional(readOnly = true)
    public UserContactResponse getContactByEmail(String email) {
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(UserContactResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }

    @Transactional(readOnly = true)
    public List<UserContactResponse> getContacts(Collection<UUID> ids) {
        return users.findAllById(ids).stream().map(UserContactResponse::from).toList();
    }

    /** JWT còn hạn không phản ánh việc vừa bị khóa, nên kiểm tra lại trong database (BR-12). */
    private User requireActiveCaller(UUID callerId) {
        User user = users.findById(callerId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Tài khoản không tồn tại"));
        if (user.isLocked()) {
            throw AuthErrors.accountLocked();
        }
        return user;
    }

    private User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }
}
