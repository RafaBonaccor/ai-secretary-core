package com.assistantcore.repository;

import com.assistantcore.model.ChannelInstance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelInstanceRepository extends JpaRepository<ChannelInstance, UUID> {
  Optional<ChannelInstance> findByPhoneNumber(String phoneNumber);
  Optional<ChannelInstance> findByInstanceName(String instanceName);
}
