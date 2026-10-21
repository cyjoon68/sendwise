package com.sendwise.api.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SenderNumberRepository extends JpaRepository<SenderNumberEntity, Long> {
    List<SenderNumberEntity> findByClientIdOrderByIdDesc(long clientId);

    Optional<SenderNumberEntity> findByClientIdAndPhone(long clientId, String phone);

    Optional<SenderNumberEntity> findByClientIdAndPhoneAndStatus(long clientId, String phone, String status);
}
