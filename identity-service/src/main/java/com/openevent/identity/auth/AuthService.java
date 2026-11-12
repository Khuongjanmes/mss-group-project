package com.openevent.identity.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.web.ApiException;
import com.openevent.identity.auth.dto.AuthResponse;
import com.openevent.identity.auth.dto.LoginRequest;
import com.openevent.identity.auth.dto.RegisterRequest;
import com.openevent.identity.auth.google.GoogleIdTokenVerifier;
import com.openevent.identity.auth.google.GoogleIdentity;
import com.openevent.identity.user.AuthProvider;
import com.openevent.identity.user.RoleName;
import com.openevent.identity.user.RoleRepository;
import com.openevent.identity.user.User;
import com.openevent.identity.user.UserRepository;
import com.openevent.identity.user.dto.UserResponse;

@Service
public class AuthService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_AVATAR_LENGTH = 500;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenService refreshTokens;
    private final JwtProperties jwtProperties;
    private final GoogleIdTokenVerifier googleVerifier;
    /** So sánh với hash giả khi không có tài khoản để thời gian phản hồi không làm lộ email nào tồn tại. */
    private final String dummyHash;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokens, RefreshTokenService refreshTokens, JwtProperties jwtProperties,
            GoogleIdTokenVerifier googleVerifier) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.jwtProperties = jwtProperties;
        this.googleVerifier = googleVerifier;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.findByEmail(email).isPresent()) {
            throw emailTaken();
        }
        User user = User.local(email, passwordEncoder.encode(request.password()), request.fullName().trim());
        return issueTokens(saveNewAttendee(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = users.findByEmail(normalizeEmail(request.email()));
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        User user = found.filter(candidate -> matches && candidate.getPasswordHash() != null)
                .orElseThrow(AuthErrors::invalidCredentials);
        // Chỉ báo khóa khi mật khẩu đúng, để người lạ không dò được trạng thái tài khoản.
        requireNotLocked(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleIdentity identity = googleVerifier.verify(idToken);
        if (!identity.emailVerified() || !isAscii(identity.email())) {
            throw AuthErrors.invalidGoogleToken();
        }
        User user = users.findByProviderAndProviderSubject(AuthProvider.GOOGLE, identity.subject())
                .orElseGet(() -> createGoogleUser(identity));
        requireNotLocked(user);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokens.consume(refreshToken);
        requireNotLocked(user);
        return issueTokens(user);
    }

    private User createGoogleUser(GoogleIdentity identity) {
        String email = normalizeEmail(identity.email());
        if (users.findByEmail(email).isPresent()) {
            // Không tự liên kết: đăng ký bằng mật khẩu không xác minh email, nên người đăng ký trước có thể
            // không phải chủ email. Liên kết sẽ giúp họ chiếm quyền truy cập vào tài khoản Google này.
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_REGISTERED_WITH_PASSWORD",
                    "Email này đã đăng ký bằng mật khẩu, vui lòng đăng nhập bằng mật khẩu");
        }
        return saveNewAttendee(User.google(email, identity.subject(), displayName(identity), avatar(identity)));
    }

    private User saveNewAttendee(User user) {
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Hai request đăng ký cùng email chạy đồng thời đều qua được bước kiểm tra ở trên.
            throw emailTaken();
        }
    }

    private AuthResponse issueTokens(User user) {
        return new AuthResponse("Bearer", accessTokens.issue(user), jwtProperties.accessTokenTtl().toSeconds(),
                refreshTokens.issue(user), UserResponse.from(user));
    }

    private static void requireNotLocked(User user) {
        if (user.isLocked()) {
            throw AuthErrors.accountLocked();
        }
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email đã được sử dụng");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isAscii(String value) {
        return StandardCharsets.US_ASCII.newEncoder().canEncode(value);
    }

    private static String displayName(GoogleIdentity identity) {
        String name = identity.name() == null || identity.name().isBlank()
                ? identity.email().substring(0, identity.email().indexOf('@'))
                : identity.name().trim();
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    private static String avatar(GoogleIdentity identity) {
        String picture = identity.picture();
        return picture != null && picture.length() <= MAX_AVATAR_LENGTH ? picture : null;
    }
}
