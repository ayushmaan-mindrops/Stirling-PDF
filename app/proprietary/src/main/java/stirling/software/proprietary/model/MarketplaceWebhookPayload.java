package stirling.software.proprietary.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Webhook payload from Azure Marketplace for subscription lifecycle events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceWebhookPayload {

    @JsonProperty("id")
    private String operationId;

    @JsonProperty("activityId")
    private String activityId;

    @JsonProperty("subscriptionId")
    private String subscriptionId;

    @JsonProperty("offerId")
    private String offerId;

    @JsonProperty("publisherId")
    private String publisherId;

    @JsonProperty("planId")
    private String planId;

    @JsonProperty("quantity")
    private int quantity;

    @JsonProperty("action")
    private String action;

    @JsonProperty("timeStamp")
    private String timestamp;

    @JsonProperty("status")
    private String status;
}
