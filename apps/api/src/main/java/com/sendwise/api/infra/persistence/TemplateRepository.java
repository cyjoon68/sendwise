package com.sendwise.api.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {
    List<TemplateEntity> findByClientIdAndStatusOrderByIdAsc(long clientId, String status);

    Optional<TemplateEntity> findByClientIdAndTemplateCodeAndStatus(long clientId, String templateCode, String status);
}
