package stirling.software.proprietary.service;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.model.AzureMarketplaceConfig;
import stirling.software.proprietary.model.MarketplaceProvisioningResult;
import stirling.software.proprietary.model.MarketplaceSubscriptionDetails;

/**
 * Service for Azure Marketplace SaaS Fulfillment API integration.
 * 
 * This service handles:
 * - Token resolution (exchanging marketplace token for subscription details)
 * - Subscription activation
 * - Subscription lifecycle management
 * 
 * API Reference: https://learn.microsoft.com/en-us/partner-center/marketplace-offers/pc-saas-fulfillment-apis
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "azure.marketplace", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AzureMarketplaceService {

    private static final String MARKETPLACE_API_VERSION = "2018-08-31";
    private static final String MARKETPLACE_API_BASE_URL = "https://marketplaceapi.microsoft.com/api/saas/subscriptions";

    private final AzureMarketplaceConfig config;
    private final RestTemplate restTemplate;

    public AzureMarketplaceService(AzureMarketplaceConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Resolve a marketplace token to get subscription details.
     * 
     * This is called when a customer is redirected from Azure Marketplace
     * with a purchase identification token.
     * 
     * @param token The marketplace purchase identification token
     * @return Subscription details from the marketplace
     */
    public MarketplaceSubscriptionDetails resolveToken(String token) {
        log.info("Resolving Azure Marketplace token");

        String url = MARKETPLACE_API_BASE_URL + "/resolve?api-version=" + MARKETPLACE_API_VERSION;

        HttpHeaders headers = createHeaders();
        headers.set("x-ms-marketplace-token", token);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            var response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    MarketplaceSubscriptionDetails.class
            );

            MarketplaceSubscriptionDetails details = response.getBody();
            if (details == null) {
                throw new RuntimeException("Empty response from marketplace resolve API");
            }

            log.info("Successfully resolved token for subscription: {}", details.getSubscriptionId());
            return details;

        } catch (Exception e) {
            log.error("Failed to resolve marketplace token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to resolve marketplace token", e);
        }
    }

    /**
     * Provision a subscription based on the marketplace subscription details.
     * 
     * This creates the necessary user/tenant in the application.
     * 
     * @param subscriptionDetails The subscription details from marketplace
     * @return Provisioning result with redirect URL
     */
    public MarketplaceProvisioningResult provisionSubscription(MarketplaceSubscriptionDetails subscriptionDetails) {
        log.info("Provisioning subscription: {}", subscriptionDetails.getSubscriptionId());

        // TODO: Implement actual user/tenant provisioning logic
        // This should:
        // 1. Create or find the user based on purchaser email
        // 2. Create a tenant/organization if needed
        // 3. Associate the subscription with the user/tenant
        // 4. Set up the appropriate plan/features based on planId

        String redirectUrl = config.getAppBaseUrl() + "/dashboard?subscription=" + subscriptionDetails.getSubscriptionId();

        return MarketplaceProvisioningResult.builder()
                .subscriptionId(subscriptionDetails.getSubscriptionId())
                .tenantId(UUID.randomUUID().toString()) // Replace with actual tenant ID
                .redirectUrl(redirectUrl)
                .success(true)
                .build();
    }

    /**
     * Activate a subscription with Azure Marketplace.
     * 
     * This must be called after provisioning to confirm the subscription is ready.
     * 
     * @param subscriptionId The subscription ID to activate
     */
    public void activateSubscription(String subscriptionId) {
        log.info("Activating subscription: {}", subscriptionId);

        String url = MARKETPLACE_API_BASE_URL + "/" + subscriptionId + "/activate?api-version=" + MARKETPLACE_API_VERSION;

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Activation request body
        var activationBody = new ActivationRequest(config.getPlanId(), config.getQuantity());

        HttpEntity<ActivationRequest> request = new HttpEntity<>(activationBody, headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
            log.info("Successfully activated subscription: {}", subscriptionId);
        } catch (Exception e) {
            log.error("Failed to activate subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new RuntimeException("Failed to activate subscription", e);
        }
    }

    /**
     * Get the current status of a subscription.
     * 
     * @param subscriptionId The subscription ID
     * @return Subscription details
     */
    public MarketplaceSubscriptionDetails getSubscription(String subscriptionId) {
        log.info("Getting subscription details: {}", subscriptionId);

        String url = MARKETPLACE_API_BASE_URL + "/" + subscriptionId + "?api-version=" + MARKETPLACE_API_VERSION;

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            var response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    MarketplaceSubscriptionDetails.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new RuntimeException("Failed to get subscription details", e);
        }
    }

    /**
     * Update the quantity (number of users) for a subscription.
     * 
     * @param subscriptionId The subscription ID
     * @param quantity The new quantity
     */
    public void updateQuantity(String subscriptionId, int quantity) {
        log.info("Updating quantity for subscription {}: {}", subscriptionId, quantity);

        String url = MARKETPLACE_API_BASE_URL + "/" + subscriptionId + "?api-version=" + MARKETPLACE_API_VERSION;

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var updateBody = new QuantityUpdateRequest(quantity);
        HttpEntity<QuantityUpdateRequest> request = new HttpEntity<>(updateBody, headers);

        try {
            restTemplate.exchange(url, HttpMethod.PATCH, request, Void.class);
            log.info("Successfully updated quantity for subscription: {}", subscriptionId);
        } catch (Exception e) {
            log.error("Failed to update quantity for subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new RuntimeException("Failed to update subscription quantity", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String getAccessToken() {
        // TODO: Implement OAuth2 token acquisition for Azure AD
        // This should use the client credentials flow to get an access token
        // for the Azure Marketplace API
        return config.getAccessToken();
    }

    // Request DTOs
    private record ActivationRequest(String planId, int quantity) {}
    private record QuantityUpdateRequest(int quantity) {}
}
