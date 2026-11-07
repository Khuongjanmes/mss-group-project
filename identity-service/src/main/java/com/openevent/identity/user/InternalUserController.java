package com.openevent.identity.user;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.identity.user.dto.UserBatchRequest;
import com.openevent.identity.user.dto.UserContactResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** API cho các service khác gọi qua Feign. Gateway chặn /internal/** từ bên ngoài. */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public UserContactResponse getById(@PathVariable UUID id) {
        return userService.getContact(id);
    }

    /** Event mời thành viên tổ chức bằng email; email so khớp không phân biệt hoa thường như lúc đăng nhập. */
    @GetMapping("/by-email")
    public UserContactResponse getByEmail(@RequestParam @NotBlank @Size(max = 254) String email) {
        return userService.getContactByEmail(email);
    }

    @PostMapping("/batch")
    public List<UserContactResponse> getBatch(@Valid @RequestBody UserBatchRequest request) {
        return userService.getContacts(request.ids());
    }
}
