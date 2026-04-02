package stirling.software.proprietary.controller.api;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import stirling.software.common.model.enumeration.Role;
import stirling.software.proprietary.security.model.AuthenticationType;
import stirling.software.proprietary.security.model.Authority;
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
    private final PasswordEncoder passwordEncoder;

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

    /**
     * Check if a Marketplace user needs to set up their account (create password).
     * This is a public endpoint - no authentication required.
     */
    @GetMapping("/setup-status")
    @Operation(
        summary = "Check Setup Status",
        description = "Check if a Marketplace user needs to create their account password"
    )
    public ResponseEntity<?> checkSetupStatus(
            @Parameter(description = "Marketplace subscription ID")
            @RequestParam("subscriptionId") String subscriptionId,
            @Parameter(description = "User email address (optional — looked up from subscription if absent)")
            @RequestParam(value = "email", required = false) String email) {
        
        log.info("Checking setup status for subscription {} and email {}", subscriptionId, email);
        
        try {
            Optional<MarketplaceSubscription> subscriptionOpt = subscriptionRepository.findBySubscriptionId(subscriptionId);
            
            if (subscriptionOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Subscription not found"
                ));
            }
            
            MarketplaceSubscription subscription = subscriptionOpt.get();

            // Resolve the effective email: prefer the caller-supplied value, fall back to DB
            String effectiveEmail = (email != null && !email.isBlank())
                    ? email
                    : subscription.getPurchaserEmail();

            if (effectiveEmail == null || effectiveEmail.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No email associated with this subscription"
                ));
            }
            
            // When both are present, verify they match
            if (email != null && !email.isBlank()
                    && subscription.getPurchaserEmail() != null
                    && !email.equalsIgnoreCase(subscription.getPurchaserEmail())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Email does not match subscription purchaser"
                ));
            }
            
            // Check if user already exists with a password
            Optional<User> existingUser = userRepository.findByUsernameIgnoreCase(effectiveEmail);
            boolean needsSetup = existingUser.isEmpty() || 
                    existingUser.get().getPassword() == null || 
                    existingUser.get().getPassword().isEmpty();
            
            return ResponseEntity.ok(Map.of(
                "needsSetup", needsSetup,
                "email", effectiveEmail,
                "planId", subscription.getPlanId() != null ? subscription.getPlanId() : "basic"
            ));
            
        } catch (Exception e) {
            log.error("Failed to check setup status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to check setup status: " + e.getMessage()
            ));
        }
    }

    /**
     * Set up account for a Marketplace user (create password and link subscription).
     * This is a public endpoint - no authentication required.
     */
    @PostMapping("/setup-account")
    @Operation(
        summary = "Setup Account",
        description = "Create account with password for a Marketplace user and link their subscription"
    )
    public ResponseEntity<?> setupAccount(@RequestBody SetupAccountRequest request) {
        
        log.info("Setting up account for subscription {} and email {}", 
                request.subscriptionId(), request.email());
        
        try {
            // Validate request
            if (request.password() == null || request.password().length() < 8) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Password must be at least 8 characters"
                ));
            }
            
            // Find the subscription
            Optional<MarketplaceSubscription> subscriptionOpt = subscriptionRepository
                    .findBySubscriptionId(request.subscriptionId());
            
            if (subscriptionOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Subscription not found"
                ));
            }
            
            MarketplaceSubscription subscription = subscriptionOpt.get();
            
            // Resolve effective email: prefer request value, fall back to DB-stored purchaser email
            String effectiveEmail = (request.email() != null && !request.email().isBlank())
                    ? request.email()
                    : subscription.getPurchaserEmail();

            if (effectiveEmail == null || effectiveEmail.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No email associated with this subscription"
                ));
            }

            // When both are present, verify they match
            if (request.email() != null && !request.email().isBlank()
                    && subscription.getPurchaserEmail() != null
                    && !request.email().equalsIgnoreCase(subscription.getPurchaserEmail())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Email does not match subscription purchaser"
                ));
            }
            
            // Create or update user
            boolean isNewUser = false;
            User user = userRepository.findByUsernameIgnoreCase(effectiveEmail)
                    .orElse(null);
            
            if (user == null) {
                isNewUser = true;
                user = new User();
                user.setUsername(effectiveEmail);
                user.setEnabled(true);
                user.setRoleName(Role.USER.getRoleId());
            }
            
            // Set password and authentication type
            user.setPassword(passwordEncoder.encode(request.password()));
            user.setAuthenticationType(AuthenticationType.WEB);
            user = userRepository.save(user);

            // Assign ROLE_USER authority for new users so Spring Security grants access
            if (isNewUser) {
                new Authority(Role.USER.getRoleId(), user);
                user = userRepository.save(user);
            }
            
            log.info("Created/updated user account for: {}", effectiveEmail);
            
            // Link subscription to user and activate
            subscription.setUser(user);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);
            
            log.info("Successfully set up account and linked subscription {} to user {}", 
                    request.subscriptionId(), effectiveEmail);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Account created successfully. You can now sign in.",
                "email", effectiveEmail,
                "planId", subscription.getPlanId() != null ? subscription.getPlanId() : "basic"
            ));
            
        } catch (Exception e) {
            log.error("Failed to setup account: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to setup account: " + e.getMessage()
            ));
        }
    }

    /**
     * DTO for account setup request.
     */
    record SetupAccountRequest(String subscriptionId, String email, String password) {}
}
