package com.sendwise.api.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportJobRepository extends JpaRepository<ExportJobEntity, Long> {
}
