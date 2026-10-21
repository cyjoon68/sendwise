package com.sendwise.api.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OptOutRepository extends JpaRepository<OptOutEntity, Long> {
    boolean existsByClientIdAndPhone(long clientId, String phone);
}
