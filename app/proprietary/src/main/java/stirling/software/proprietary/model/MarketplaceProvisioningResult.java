package stirling.software.proprietary.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of provisioning a marketplace subscription.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceProvisioningResult {

    private String subscriptionId;
    
    private String tenantId;
    
    private String redirectUrl;
    
    private boolean success;
    
    private String errorMessage;
}
