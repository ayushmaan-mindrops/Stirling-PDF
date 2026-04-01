package stirling.software.proprietary.controller.api;

import java.security.Principal;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.model.MarketplaceSubscription;
import stirling.software.proprietary.model.MarketplaceSubscription.SubscriptionStatus;
import stirling.software.proprietary.repository.MarketplaceSubscriptionRepository;
import stirling.software.proprietary.security.database.repository.UserRepository;
import stirling.software.proprietary.security.model.User;

/**
 * Controller for linking Azure Marketplace subscriptions to authenticated users.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
@Tag(name = "Azure Marketplace", description = "Azure Marketplace subscription management")
@ConditionalOnProperty(prefix = "azure.marketplace", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MarketplaceSubscriptionController {

    private final MarketplaceSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /**
     * Link a Marketplace subscription to the currently authenticated user.
     * 
     * This endpoint is called after a user successfully authenticates following
     * a Marketplace purchase redirect. It links the subscription to their account
     * and activates their entitlements.
     * 
     * @param subscriptionId The Marketplace subscription ID to link
     * @param principal The authenticated user's principal
     * @return Success or error response
     */
    @PostMapping("/link-subscription")
    @Operation(
        summary = "Link Marketplace Subscription",
        description = "Links an Azure Marketplace subscription to the authenticated user's account"
    )
    public ResponseEntity<?> linkSubscription(
            @Parameter(description = "Marketplace subscription ID")
            @RequestParam("subscriptionId") String subscriptionId,
            Principal principal) {
        
        if (principal == null) {
            log.warn("Attempted to link subscription without authentication");
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Authentication required"
            ));
        }

        String username = principal.getName();
        log.info("Linking subscription {} to user {}", subscriptionId, username);

        try {
            // Find the subscription
            MarketplaceSubscription subscription = subscriptionRepository
                    .findBySubscriptionId(subscriptionId)
                    .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

            // Check if already linked to a different user
            if (subscription.getUser() != null && !subscription.getUser().getUsername().equals(username)) {
                log.warn("Subscription {} already linked to different user: {}", 
                        subscriptionId, subscription.getUser().getUsername());
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Subscription already linked to another account"
                ));
            }

            // Find the current user
            User user = userRepository.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            // Link subscription to user
            subscription.setUser(user);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);

            log.info("Successfully linked subscription {} to user {}", subscriptionId, username);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Subscription linked successfully",
                "subscriptionId", subscriptionId,
                "planId", subscription.getPlanId(),
                "quantity", subscription.getQuantity()
            ));

        } catch (Exception e) {
            log.error("Failed to link subscription {}: {}", subscriptionId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to link subscription: " + e.getMessage()
            ));
        }
    }
}
