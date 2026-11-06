package com.openevent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.openevent.identity.bootstrap.AdminBootstrap;
import com.openevent.identity.support.IdentityIntegrationTest;

class BootstrapAdminIT extends IdentityIntegrationTest {

    @Autowired
    AdminBootstrap bootstrap;

    @Test
    void runningAgainDoesNotCreateDuplicateOrResetPassword() throws Exception {
        bootstrap.run(null);

        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, ADMIN_EMAIL)).isEqualTo(1);
        assertThat(login(ADMIN_EMAIL, ADMIN_PASSWORD).status()).isEqualTo(200);
    }
}
