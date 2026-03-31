package stirling.software.proprietary.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents subscription details returned from Azure Marketplace SaaS Fulfillment API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceSubscriptionDetails {

    @JsonProperty("id")
    private String subscriptionId;

    @JsonProperty("subscriptionName")
    private String subscriptionName;

    @JsonProperty("offerId")
    private String offerId;

    @JsonProperty("planId")
    private String planId;

    @JsonProperty("quantity")
    private int quantity;

    @JsonProperty("subscription")
    private SubscriptionInfo subscription;

    @JsonProperty("purchaser")
    private Purchaser purchaser;

    @JsonProperty("beneficiary")
    private Beneficiary beneficiary;

    @JsonProperty("saasSubscriptionStatus")
    private String subscriptionStatus;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("publisherId")
        private String publisherId;

        @JsonProperty("offerId")
        private String offerId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("saasSubscriptionStatus")
        private String status;

        @JsonProperty("beneficiary")
        private Beneficiary beneficiary;

        @JsonProperty("purchaser")
        private Purchaser purchaser;

        @JsonProperty("planId")
        private String planId;

        @JsonProperty("quantity")
        private int quantity;

        @JsonProperty("term")
        private Term term;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Purchaser {
        @JsonProperty("emailId")
        private String email;

        @JsonProperty("objectId")
        private String objectId;

        @JsonProperty("tenantId")
        private String tenantId;

        @JsonProperty("puid")
        private String puid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Beneficiary {
        @JsonProperty("emailId")
        private String email;

        @JsonProperty("objectId")
        private String objectId;

        @JsonProperty("tenantId")
        private String tenantId;

        @JsonProperty("puid")
        private String puid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Term {
        @JsonProperty("termUnit")
        private String termUnit;

        @JsonProperty("startDate")
        private String startDate;

        @JsonProperty("endDate")
        private String endDate;
    }
}
