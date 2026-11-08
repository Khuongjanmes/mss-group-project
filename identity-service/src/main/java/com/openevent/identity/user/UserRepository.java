package com.openevent.identity.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    List<User> findAllByIdIn(Collection<UUID> ids);

    Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
