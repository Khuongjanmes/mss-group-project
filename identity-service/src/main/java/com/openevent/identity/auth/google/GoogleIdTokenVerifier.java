package com.openevent.identity.auth.google;

public interface GoogleIdTokenVerifier {

    GoogleIdentity verify(String idToken);
}
