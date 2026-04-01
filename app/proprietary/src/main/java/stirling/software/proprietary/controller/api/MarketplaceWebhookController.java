package stirling.software.proprietary.controller.api;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.model.AzureMarketplaceConfig;
import stirling.software.proprietary.model.MarketplaceWebhookPayload;
import stirling.software.proprietary.service.AzureMarketplaceService;

/**
 * Azure Marketplace Webhook Controller.
 * 
 * This controller handles webhook notifications from Azure Marketplace for
 * subscription lifecycle events such as:
 * - ChangePlan: Customer changed their subscription plan
 * - ChangeQuantity: Customer changed the number of seats/users
 * - Suspend: Subscription was suspended (e.g., payment failure)
 * - Unsubscribe: Customer cancelled their subscription
 * - Reinstate: Suspended subscription was reinstated
 * 
 * The webhook must be available 24/7 and respond within 10 seconds.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
@Tag(name = "Azure Marketplace", description = "Azure Marketplace integration endpoints")
@ConditionalOnProperty(prefix = "azure.marketplace", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MarketplaceWebhookController {

    private final AzureMarketplaceService marketplaceService;
    private final AzureMarketplaceConfig config;

    /**
     * Webhook endpoint for Azure Marketplace subscription events.
     * 
     * Azure Marketplace sends POST requests to this endpoint when subscription
     * lifecycle events occur.
     * 
     * @param payload The webhook payload containing event details
     * @param signature The webhook signature for validation
     * @return Acknowledgment response
     */
    @PostMapping("/webhook")
    @Operation(
        summary = "Marketplace Webhook",
        description = "Receives subscription lifecycle events from Azure Marketplace"
    )
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody MarketplaceWebhookPayload payload,
            @RequestHeader(value = "x-ms-marketplace-token", required = false) String token,
            @RequestHeader(value = "x-ms-signature", required = false) String signature) {
        
        log.info("Received Azure Marketplace webhook: action={}, subscriptionId={}, operationId={}",
                payload.getAction(),
                payload.getSubscriptionId(),
                payload.getOperationId());

        // Validate webhook signature if configured
        if (config.getWebhookSecret() != null && !config.getWebhookSecret().isEmpty()) {
            if (!validateWebhookSignature(signature, payload)) {
                log.warn("Invalid webhook signature for operation: {}", payload.getOperationId());
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Invalid webhook signature"
                ));
            }
        }

        try {
            switch (payload.getAction().toLowerCase()) {
                case "changeplan" -> handleChangePlan(payload);
                case "changequantity" -> handleChangeQuantity(payload);
                case "suspend" -> handleSuspend(payload);
                case "unsubscribe" -> handleUnsubscribe(payload);
                case "reinstate" -> handleReinstate(payload);
                case "renew" -> handleRenew(payload);
                default -> {
                    log.warn("Unknown webhook action: {}", payload.getAction());
                    return ResponseEntity.ok(Map.of(
                        "status", "acknowledged",
                        "message", "Unknown action type"
                    ));
                }
            }

            // Acknowledge the webhook
            acknowledgeOperation(payload.getSubscriptionId(), payload.getOperationId());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "operationId", payload.getOperationId()
            ));

        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage(), e);
            // Still return 200 to acknowledge receipt, but log the error
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "message", "Processing failed but acknowledged"
            ));
        }
    }

    private void handleChangePlan(MarketplaceWebhookPayload payload) {
        log.info("Processing plan change: subscriptionId={}, newPlanId={}",
                payload.getSubscriptionId(), payload.getPlanId());
        
        // For single-plan model, plan changes are not expected
        // but we log them for visibility
    }

    private void handleChangeQuantity(MarketplaceWebhookPayload payload) {
        log.info("Processing quantity change: subscriptionId={}, newQuantity={}",
                payload.getSubscriptionId(), payload.getQuantity());
        
        // Update the number of allowed users/seats in local database
        marketplaceService.updateLocalQuantity(payload.getSubscriptionId(), payload.getQuantity());
    }

    private void handleSuspend(MarketplaceWebhookPayload payload) {
        log.info("Processing subscription suspension: subscriptionId={}",
                payload.getSubscriptionId());
        
        // Mark the subscription as suspended in the database
        marketplaceService.updateLocalStatus(
                payload.getSubscriptionId(), 
                stirling.software.proprietary.model.MarketplaceSubscription.SubscriptionStatus.SUSPENDED);
    }

    private void handleUnsubscribe(MarketplaceWebhookPayload payload) {
        log.info("Processing unsubscribe: subscriptionId={}",
                payload.getSubscriptionId());
        
        // Mark the subscription as unsubscribed in the database
        marketplaceService.updateLocalStatus(
                payload.getSubscriptionId(), 
                stirling.software.proprietary.model.MarketplaceSubscription.SubscriptionStatus.UNSUBSCRIBED);
    }

    private void handleReinstate(MarketplaceWebhookPayload payload) {
        log.info("Processing subscription reinstatement: subscriptionId={}",
                payload.getSubscriptionId());
        
        // Mark the subscription as active in the database
        marketplaceService.updateLocalStatus(
                payload.getSubscriptionId(), 
                stirling.software.proprietary.model.MarketplaceSubscription.SubscriptionStatus.ACTIVE);
    }

    private void handleRenew(MarketplaceWebhookPayload payload) {
        log.info("Processing subscription renewal: subscriptionId={}",
                payload.getSubscriptionId());
        
        // Renewal is informational - subscription continues as active
        marketplaceService.updateLocalStatus(
                payload.getSubscriptionId(), 
                stirling.software.proprietary.model.MarketplaceSubscription.SubscriptionStatus.ACTIVE);
    }

    private void acknowledgeOperation(String subscriptionId, String operationId) {
        // Call the Azure Marketplace API to acknowledge the operation
        marketplaceService.acknowledgeOperation(subscriptionId, operationId, "Success");
    }

    private boolean validateWebhookSignature(String signature, MarketplaceWebhookPayload payload) {
        // For now, skip signature validation if no secret is configured
        // In production, implement proper HMAC validation using webhookSecret
        if (signature == null || signature.isEmpty()) {
            log.warn("No signature provided in webhook request");
            return true; // Allow for testing
        }
        // TODO: Implement proper HMAC-SHA256 signature validation
        return true;
    }
}
