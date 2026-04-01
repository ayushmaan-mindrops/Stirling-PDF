package stirling.software.proprietary.controller.api;

import java.net.URI;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<?> handleLanding(
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
            
            // Activate the subscription with Azure using resolved planId and quantity
            marketplaceService.activateSubscription(
                    subscriptionDetails.getSubscriptionId(),
                    subscriptionDetails.getPlanId(),
                    subscriptionDetails.getQuantity());
            
            log.info("Successfully activated Azure Marketplace subscription: {}",
                    subscriptionDetails.getSubscriptionId());

            // Return a simple HTML welcome page that redirects to login
            // This avoids issues with ngrok interstitial pages breaking the SPA
            String welcomeHtml = buildWelcomeHtml(
                    provisioningResult.getRedirectUrl(),
                    subscriptionDetails.getSubscriptionId(),
                    subscriptionDetails.getPlanId());
            
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(welcomeHtml);
            
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

    /**
     * Build a simple HTML welcome page for Marketplace customers.
     * This avoids issues with ngrok interstitial pages breaking the SPA routing.
     */
    private String buildWelcomeHtml(String redirectUrl, String subscriptionId, String planId) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Welcome to PaperBolt - Azure Marketplace</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .container {
                        background: white;
                        border-radius: 16px;
                        padding: 48px;
                        max-width: 480px;
                        width: 100%%;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
                        text-align: center;
                    }
                    .logo {
                        font-size: 48px;
                        margin-bottom: 16px;
                    }
                    h1 {
                        color: #1a1a2e;
                        font-size: 28px;
                        font-weight: 700;
                        margin-bottom: 12px;
                    }
                    .success-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 8px;
                        background: #d1fae5;
                        color: #065f46;
                        padding: 8px 16px;
                        border-radius: 9999px;
                        font-size: 14px;
                        font-weight: 600;
                        margin-bottom: 24px;
                    }
                    .success-badge::before {
                        content: '✓';
                        font-weight: bold;
                    }
                    p {
                        color: #4a5568;
                        font-size: 16px;
                        line-height: 1.6;
                        margin-bottom: 24px;
                    }
                    .details {
                        background: #f7fafc;
                        border-radius: 8px;
                        padding: 16px;
                        margin-bottom: 32px;
                        text-align: left;
                    }
                    .details-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 8px 0;
                        border-bottom: 1px solid #e2e8f0;
                    }
                    .details-row:last-child {
                        border-bottom: none;
                    }
                    .details-label {
                        color: #718096;
                        font-size: 14px;
                    }
                    .details-value {
                        color: #2d3748;
                        font-size: 14px;
                        font-weight: 600;
                    }
                    .cta-button {
                        display: inline-block;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        color: white;
                        padding: 16px 48px;
                        border-radius: 12px;
                        font-size: 18px;
                        font-weight: 600;
                        text-decoration: none;
                        transition: transform 0.2s, box-shadow 0.2s;
                        border: none;
                        cursor: pointer;
                    }
                    .cta-button:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 10px 20px rgba(102, 126, 234, 0.4);
                    }
                    .footer {
                        margin-top: 32px;
                        color: #a0aec0;
                        font-size: 13px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="logo">📄</div>
                    <h1>Welcome to PaperBolt!</h1>
                    <div class="success-badge">Subscription Activated</div>
                    <p>Your Azure Marketplace purchase has been successfully processed. You now have access to PaperBolt's professional PDF tools.</p>
                    <div class="details">
                        <div class="details-row">
                            <span class="details-label">Subscription ID</span>
                            <span class="details-value">%s</span>
                        </div>
                        <div class="details-row">
                            <span class="details-label">Plan</span>
                            <span class="details-value">%s</span>
                        </div>
                        <div class="details-row">
                            <span class="details-label">Status</span>
                            <span class="details-value" style="color: #059669;">Active</span>
                        </div>
                    </div>
                    <a href="%s" class="cta-button">Continue to PaperBolt →</a>
                    <p class="footer">You'll be asked to sign in or create an account to access your subscription.</p>
                </div>
            </body>
            </html>
            """.formatted(
                subscriptionId.substring(0, Math.min(8, subscriptionId.length())) + "...",
                planId.substring(0, 1).toUpperCase() + planId.substring(1),
                redirectUrl
            );
    }
}
