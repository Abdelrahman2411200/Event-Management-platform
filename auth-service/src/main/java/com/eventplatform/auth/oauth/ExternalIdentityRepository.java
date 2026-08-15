package com.eventplatform.auth.oauth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
