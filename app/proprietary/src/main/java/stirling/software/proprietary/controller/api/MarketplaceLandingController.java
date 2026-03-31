package stirling.software.proprietary.controller.api;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.service.AzureMarketplaceService;

/**
 * Azure Marketplace Landing Page Controller.
 * 
 * This controller handles the landing page flow for Azure Marketplace SaaS offers.
 * When a customer purchases the SaaS offer from Azure Marketplace, they are redirected
 * to this landing page with a marketplace token that needs to be resolved.
 * 
 * Flow:
 * 1. Customer purchases SaaS offer in Azure Marketplace
 * 2. Azure redirects to this landing page with a token parameter
 * 3. This controller calls the SaaS Fulfillment API to resolve the token
 * 4. The subscription is activated and the user is provisioned
 * 5. User is redirected to the application
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
@Tag(name = "Azure Marketplace", description = "Azure Marketplace integration endpoints")
@ConditionalOnProperty(prefix = "azure.marketplace", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MarketplaceLandingController {

    private final AzureMarketplaceService marketplaceService;

    /**
     * Landing page endpoint for Azure Marketplace.
     * 
     * This endpoint receives the marketplace purchase identification token
     * when a customer is redirected from Azure Marketplace after purchasing
     * the SaaS offer.
     * 
     * @param token The marketplace purchase identification token
     * @return Redirect to the application or error page
     */
    @GetMapping("/landing")
    @Operation(
        summary = "Azure Marketplace Landing Page",
        description = "Handles the redirect from Azure Marketplace after a customer purchases the SaaS offer"
    )
    public ResponseEntity<Map<String, Object>> handleLanding(
            @Parameter(description = "Marketplace purchase identification token")
            @RequestParam("token") String token) {
        
        log.info("Received Azure Marketplace landing request with token");
        
        try {
            // Resolve the marketplace token to get subscription details
            var subscriptionDetails = marketplaceService.resolveToken(token);
            
            log.info("Resolved marketplace subscription: subscriptionId={}, planId={}, offerId={}",
                    subscriptionDetails.getSubscriptionId(),
                    subscriptionDetails.getPlanId(),
                    subscriptionDetails.getOfferId());
            
            // Provision the user/tenant based on subscription details
            var provisioningResult = marketplaceService.provisionSubscription(subscriptionDetails);
            
            // Activate the subscription with Azure
            marketplaceService.activateSubscription(subscriptionDetails.getSubscriptionId());
            
            log.info("Successfully activated Azure Marketplace subscription: {}",
                    subscriptionDetails.getSubscriptionId());
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Subscription activated successfully",
                "subscriptionId", subscriptionDetails.getSubscriptionId(),
                "redirectUrl", provisioningResult.getRedirectUrl()
            ));
            
        } catch (Exception e) {
            log.error("Failed to process Azure Marketplace landing: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to process marketplace subscription: " + e.getMessage()
            ));
        }
    }

    /**
     * Health check endpoint for Azure Marketplace integration.
     * Azure Marketplace requires the landing page to be available 24/7.
     */
    @GetMapping("/health")
    @Operation(
        summary = "Marketplace Health Check",
        description = "Health check endpoint for Azure Marketplace integration"
    )
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "azure-marketplace"
        ));
    }

    /**
     * Endpoint to handle subscription management requests.
     * This is called when a customer manages their subscription from Azure portal.
     */
    @PostMapping("/manage")
    @Operation(
        summary = "Manage Subscription",
        description = "Handle subscription management requests from Azure portal"
    )
    public ResponseEntity<Map<String, Object>> manageSubscription(
            @Parameter(description = "Marketplace token for subscription management")
            @RequestParam("token") String token) {
        
        log.info("Received Azure Marketplace management request");
        
        try {
            var subscriptionDetails = marketplaceService.resolveToken(token);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "subscriptionId", subscriptionDetails.getSubscriptionId(),
                "planId", subscriptionDetails.getPlanId(),
                "quantity", subscriptionDetails.getQuantity(),
                "subscriptionStatus", subscriptionDetails.getSubscriptionStatus()
            ));
            
        } catch (Exception e) {
            log.error("Failed to process subscription management request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to retrieve subscription details: " + e.getMessage()
            ));
        }
    }
}
