package stirling.software.proprietary.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.model.AzureMarketplaceConfig;
import stirling.software.proprietary.model.MarketplaceProvisioningResult;
import stirling.software.proprietary.model.MarketplaceSubscription;
import stirling.software.proprietary.model.MarketplaceSubscription.SubscriptionStatus;
import stirling.software.proprietary.model.MarketplaceSubscriptionDetails;
import stirling.software.proprietary.repository.MarketplaceSubscriptionRepository;
import stirling.software.proprietary.security.database.repository.UserRepository;
import stirling.software.proprietary.security.model.User;

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
    private static final String MARKETPLACE_FULFILLMENT_SCOPE = "20e940b3-4c77-4b0b-9a53-9e16a1b010a7/.default";

    private final AzureMarketplaceConfig config;
    private final RestTemplate restTemplate;
    private final MarketplaceSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    // Cached access token
    private String cachedAccessToken;
    private Instant tokenExpiresAt;

    public AzureMarketplaceService(
            AzureMarketplaceConfig config,
            MarketplaceSubscriptionRepository subscriptionRepository,
            UserRepository userRepository) {
        this.config = config;
        this.restTemplate = new RestTemplate();
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
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

        try {
            // Extract purchaser info
            String purchaserEmail = null;
            String purchaserObjectId = null;
            String purchaserTenantId = null;

            if (subscriptionDetails.getPurchaser() != null) {
                purchaserEmail = subscriptionDetails.getPurchaser().getEmail();
                purchaserObjectId = subscriptionDetails.getPurchaser().getObjectId();
                purchaserTenantId = subscriptionDetails.getPurchaser().getTenantId();
            }

            // Check if subscription already exists
            Optional<MarketplaceSubscription> existingSubscription = 
                    subscriptionRepository.findBySubscriptionId(subscriptionDetails.getSubscriptionId());
            
            if (existingSubscription.isPresent()) {
                log.info("Subscription already exists: {}", subscriptionDetails.getSubscriptionId());
                String redirectUrl = config.getAppBaseUrl()
                        + "/login?marketplace=1&subscription="
                        + subscriptionDetails.getSubscriptionId()
                        + (purchaserEmail != null ? "&email=" + URLEncoder.encode(purchaserEmail, StandardCharsets.UTF_8) : "");
                log.info("Marketplace redirect URL (existing): {}", redirectUrl);
                return MarketplaceProvisioningResult.builder()
                        .subscriptionId(subscriptionDetails.getSubscriptionId())
                        .tenantId(purchaserTenantId)
                        .redirectUrl(redirectUrl)
                        .success(true)
                        .build();
            }

            // Don't auto-create user - will be linked after user signs in
            User user = null;
            if (purchaserEmail != null && !purchaserEmail.isEmpty()) {
                Optional<User> existingUser = userRepository.findByUsernameIgnoreCase(purchaserEmail);
                if (existingUser.isPresent()) {
                    user = existingUser.get();
                    log.info("Found existing user for purchaser: {}", purchaserEmail);
                }
            }

            // Create marketplace subscription record
            MarketplaceSubscription subscription = MarketplaceSubscription.builder()
                    .subscriptionId(subscriptionDetails.getSubscriptionId())
                    .purchaserEmail(purchaserEmail)
                    .purchaserObjectId(purchaserObjectId)
                    .purchaserTenantId(purchaserTenantId)
                    .planId(subscriptionDetails.getPlanId())
                    .offerId(subscriptionDetails.getOfferId())
                    .quantity(subscriptionDetails.getQuantity())
                    .status(SubscriptionStatus.PENDING)
                    .user(user)
                    .build();

            subscriptionRepository.save(subscription);
            log.info("Created marketplace subscription record: {}", subscription.getSubscriptionId());

            String redirectUrl = config.getAppBaseUrl()
                    + "/login?marketplace=1&subscription="
                    + subscriptionDetails.getSubscriptionId()
                    + (purchaserEmail != null ? "&email=" + URLEncoder.encode(purchaserEmail, StandardCharsets.UTF_8) : "");
            log.info("Marketplace redirect URL (new): {}", redirectUrl);

            return MarketplaceProvisioningResult.builder()
                    .subscriptionId(subscriptionDetails.getSubscriptionId())
                    .tenantId(purchaserTenantId)
                    .redirectUrl(redirectUrl)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to provision subscription: {}", e.getMessage(), e);
            return MarketplaceProvisioningResult.builder()
                    .subscriptionId(subscriptionDetails.getSubscriptionId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Activate a subscription with Azure Marketplace.
     * 
     * This must be called after provisioning to confirm the subscription is ready.
     * 
     * @param subscriptionId The subscription ID to activate
     */
    public void activateSubscription(String subscriptionId, String planId, int quantity) {
        log.info("Activating subscription: {} with planId={}, quantity={}", subscriptionId, planId, quantity);

        String url = MARKETPLACE_API_BASE_URL + "/" + subscriptionId + "/activate?api-version=" + MARKETPLACE_API_VERSION;

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Activation request body - use resolved planId and quantity
        var activationBody = new ActivationRequest(planId, quantity);

        HttpEntity<ActivationRequest> request = new HttpEntity<>(activationBody, headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
            log.info("Successfully activated subscription: {}", subscriptionId);

            // Update subscription status to ACTIVE
            subscriptionRepository.findBySubscriptionId(subscriptionId).ifPresent(subscription -> {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscriptionRepository.save(subscription);
                log.info("Updated subscription status to ACTIVE: {}", subscriptionId);
            });

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

    /**
     * Acknowledge a webhook operation with Azure Marketplace.
     * This confirms that we've processed the webhook.
     */
    public void acknowledgeOperation(String subscriptionId, String operationId, String status) {
        log.info("Acknowledging operation: subscriptionId={}, operationId={}, status={}",
                subscriptionId, operationId, status);

        String url = MARKETPLACE_API_BASE_URL + "/" + subscriptionId + "/operations/" + operationId 
                + "?api-version=" + MARKETPLACE_API_VERSION;

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var ackBody = Map.of("status", status);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(ackBody, headers);

        try {
            restTemplate.exchange(url, HttpMethod.PATCH, request, Void.class);
            log.info("Successfully acknowledged operation: {}", operationId);
        } catch (Exception e) {
            log.error("Failed to acknowledge operation {}: {}", operationId, e.getMessage(), e);
            // Don't throw - acknowledgment failure shouldn't break the flow
        }
    }

    /**
     * Update subscription quantity in local database.
     */
    public void updateLocalQuantity(String subscriptionId, int quantity) {
        subscriptionRepository.findBySubscriptionId(subscriptionId).ifPresent(subscription -> {
            subscription.setQuantity(quantity);
            subscriptionRepository.save(subscription);
            log.info("Updated local quantity for subscription {}: {}", subscriptionId, quantity);
        });
    }

    /**
     * Update subscription status in local database.
     */
    public void updateLocalStatus(String subscriptionId, SubscriptionStatus status) {
        subscriptionRepository.findBySubscriptionId(subscriptionId).ifPresent(subscription -> {
            subscription.setStatus(status);
            subscriptionRepository.save(subscription);
            log.info("Updated local status for subscription {}: {}", subscriptionId, status);
        });
    }

    private String getAccessToken() {
        // Check if we have a valid cached token
        if (cachedAccessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedAccessToken;
        }

        log.info("Acquiring new access token for Azure Marketplace API");

        String tokenUrl = "https://login.microsoftonline.com/" + config.getTenantId() + "/oauth2/v2.0/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());
        body.add("scope", MARKETPLACE_FULFILLMENT_SCOPE);
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            var response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );

            TokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new RuntimeException("Empty token response from Azure AD");
            }

            cachedAccessToken = tokenResponse.accessToken();
            // Set expiry with 5 minute buffer
            tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn() - 300);

            log.info("Successfully acquired access token, expires in {} seconds", tokenResponse.expiresIn());
            return cachedAccessToken;

        } catch (Exception e) {
            log.error("Failed to acquire access token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to acquire Azure Marketplace access token", e);
        }
    }

    // Token response DTO
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") int expiresIn
    ) {}

    // Request DTOs
    private record ActivationRequest(String planId, int quantity) {}
    private record QuantityUpdateRequest(int quantity) {}
}
