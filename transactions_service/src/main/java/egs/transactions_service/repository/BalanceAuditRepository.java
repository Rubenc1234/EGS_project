package egs.transactions_service.repository;

import egs.transactions_service.entity.BalanceAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BalanceAuditRepository extends JpaRepository<BalanceAudit, Long> {
}
