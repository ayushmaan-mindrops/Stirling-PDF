package stirling.software.proprietary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import stirling.software.proprietary.model.MarketplaceSubscription;
import stirling.software.proprietary.model.MarketplaceSubscription.SubscriptionStatus;
import stirling.software.proprietary.security.model.User;

/**
 * Repository for Azure Marketplace subscriptions.
 */
@Repository
public interface MarketplaceSubscriptionRepository extends JpaRepository<MarketplaceSubscription, Long> {

    /**
     * Find subscription by Azure Marketplace subscription ID.
     */
    Optional<MarketplaceSubscription> findBySubscriptionId(String subscriptionId);

    /**
     * Find all subscriptions for a user.
     */
    List<MarketplaceSubscription> findByUser(User user);

    /**
     * Find subscription by purchaser email.
     */
    Optional<MarketplaceSubscription> findByPurchaserEmailIgnoreCase(String purchaserEmail);

    /**
     * Find all active subscriptions for a user.
     */
    List<MarketplaceSubscription> findByUserAndStatus(User user, SubscriptionStatus status);

    /**
     * Check if a subscription exists by subscription ID.
     */
    boolean existsBySubscriptionId(String subscriptionId);
}
