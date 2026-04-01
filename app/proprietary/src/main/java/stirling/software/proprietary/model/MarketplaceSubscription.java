package stirling.software.proprietary.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import stirling.software.proprietary.security.model.User;

/**
 * Entity representing an Azure Marketplace subscription.
 * Links a Marketplace subscription to a purchaser user in PaperBolt.
 */
@Entity
@Table(name = "marketplace_subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Azure Marketplace subscription ID (from resolve API).
     */
    @Column(name = "subscription_id", unique = true, nullable = false)
    private String subscriptionId;

    /**
     * Purchaser email from Marketplace.
     */
    @Column(name = "purchaser_email")
    private String purchaserEmail;

    /**
     * Purchaser Azure AD object ID.
     */
    @Column(name = "purchaser_object_id")
    private String purchaserObjectId;

    /**
     * Purchaser tenant ID.
     */
    @Column(name = "purchaser_tenant_id")
    private String purchaserTenantId;

    /**
     * The plan ID from Marketplace (e.g., "basic").
     */
    @Column(name = "plan_id", nullable = false)
    private String planId;

    /**
     * The offer ID from Marketplace.
     */
    @Column(name = "offer_id")
    private String offerId;

    /**
     * Number of seats/users purchased.
     */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Subscription status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    /**
     * The PaperBolt user this subscription is attached to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Subscription status enum.
     */
    public enum SubscriptionStatus {
        PENDING,
        ACTIVE,
        SUSPENDED,
        UNSUBSCRIBED
    }
}
