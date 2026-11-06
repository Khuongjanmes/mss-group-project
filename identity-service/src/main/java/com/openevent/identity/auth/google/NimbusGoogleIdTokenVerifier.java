package com.openevent.identity.auth.google;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;

import com.openevent.common.web.ApiException;
import com.openevent.identity.auth.AuthErrors;

/**
 * Xác minh ID token của Google. Decoder kiểm tra chữ ký bằng khóa công khai của Google và thời hạn; lớp này
 * kiểm tra token được cấp cho đúng ứng dụng và đúng nhà phát hành.
 */
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private final JwtDecoder decoder;

    public NimbusGoogleIdTokenVerifier(String clientId, JwtDecoder decoder) {
        this.clientId = clientId;
        this.decoder = decoder;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        Jwt jwt = decode(idToken);
        List<String> audience = jwt.getAudience();
        String email = jwt.getClaimAsString("email");
        if (!GOOGLE_ISSUERS.contains(jwt.getClaimAsString("iss"))
                || audience == null || !audience.contains(clientId)
                || !StringUtils.hasText(jwt.getSubject())
                || !StringUtils.hasText(email)) {
            throw AuthErrors.invalidGoogleToken();
        }
        return new GoogleIdentity(jwt.getSubject(), email.toLowerCase(Locale.ROOT), jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture"), Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));
    }

    private Jwt decode(String idToken) {
        try {
            return decoder.decode(idToken);
        } catch (BadJwtException ex) {
            throw AuthErrors.invalidGoogleToken();
        } catch (JwtException ex) {
            // Không lấy được khóa công khai của Google: lỗi tạm thời, không phải token sai.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_UNAVAILABLE",
                    "Không thể xác minh với Google, vui lòng thử lại");
        }
    }
}
