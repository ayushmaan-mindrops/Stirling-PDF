package stirling.software.proprietary.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for Azure Marketplace integration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "azure.marketplace")
@ConditionalOnProperty(prefix = "azure.marketplace", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AzureMarketplaceConfig {

    private boolean enabled = false;
    
    private String tenantId;
    
    private String clientId;
    
    private String clientSecret;
    
    private String webhookSecret;
    
    /** Public URL of the SPA (scheme + host + optional subpath). Used for Marketplace "Continue" links; must match how users load the frontend (no trailing slash required). */
    private String appBaseUrl;
}
