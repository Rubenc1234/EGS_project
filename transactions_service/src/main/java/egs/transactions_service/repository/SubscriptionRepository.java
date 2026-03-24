package egs.transactions_service.repository;

import egs.transactions_service.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    List<Subscription> findByUserId(String userId);
    Optional<Subscription> findByUserIdAndDeviceId(String userId, String deviceId);
    void deleteByUserIdAndDeviceId(String userId, String deviceId);
}
