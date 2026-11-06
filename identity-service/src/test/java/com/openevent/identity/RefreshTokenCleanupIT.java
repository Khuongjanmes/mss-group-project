package com.openevent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.openevent.identity.auth.RefreshTokenService;
import com.openevent.identity.support.IdentityIntegrationTest;

class RefreshTokenCleanupIT extends IdentityIntegrationTest {

    @Autowired
    RefreshTokenService refreshTokens;

    @Test
    void removesOnlyExpiredTokens() {
        Response expired = register(uniqueEmail());
        Response active = register(uniqueEmail());
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute' where user_id = ?::uuid",
                attendee(expired).id());

        refreshTokens.deleteExpired();

        assertThat(count(attendee(expired).id())).isZero();
        assertThat(count(attendee(active).id())).isEqualTo(1);
    }

    private Integer count(String userId) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?::uuid", Integer.class, userId);
    }
}
