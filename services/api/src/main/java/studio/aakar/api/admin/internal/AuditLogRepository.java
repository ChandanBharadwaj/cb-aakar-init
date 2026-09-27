package studio.aakar.api.admin.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
}
