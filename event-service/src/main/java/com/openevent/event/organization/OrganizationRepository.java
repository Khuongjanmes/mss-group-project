package com.openevent.event.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    @Query("select m from Organization o join o.members m where o.id = :organizationId and key(m) = :userId")
    Optional<Membership> findMembership(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    @Query("select o from Organization o join o.members m where key(m) = :userId order by o.name")
    List<Organization> findAllByMember(@Param("userId") UUID userId);
}
