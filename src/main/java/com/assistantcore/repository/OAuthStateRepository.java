package com.assistantcore.repository;

import com.assistantcore.model.OAuthState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateRepository extends JpaRepository<OAuthState, UUID> {
  Optional<OAuthState> findByStateToken(String stateToken);
}
