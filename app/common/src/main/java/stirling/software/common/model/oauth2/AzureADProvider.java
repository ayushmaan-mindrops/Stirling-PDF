package stirling.software.common.model.oauth2;

import java.util.ArrayList;
import java.util.Collection;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import stirling.software.common.model.enumeration.UsernameAttribute;

/**
 * Azure AD (Microsoft Entra ID) OAuth2/OIDC Provider configuration.
 * Supports both single-tenant and multi-tenant Azure AD applications.
 */
@Getter
@Setter
@NoArgsConstructor
public class AzureADProvider extends Provider {

    private static final String NAME = "azure";
    private static final String CLIENT_NAME = "Microsoft";
    private static final String AUTHORIZATION_URI_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String TOKEN_URI_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String USER_INFO_URI = "https://graph.microsoft.com/oidc/userinfo";
    private static final String ISSUER_TEMPLATE = "https://login.microsoftonline.com/%s/v2.0";

    private String tenantId = "common"; // Default to multi-tenant

    public AzureADProvider(
            String tenantId,
            String clientId,
            String clientSecret,
            Collection<String> scopes,
            UsernameAttribute useAsUsername) {
        super(
                String.format(ISSUER_TEMPLATE, tenantId != null ? tenantId : "common"),
                NAME,
                CLIENT_NAME,
                clientId,
                clientSecret,
                scopes,
                useAsUsername,
                String.format(AUTHORIZATION_URI_TEMPLATE, tenantId != null ? tenantId : "common"),
                String.format(TOKEN_URI_TEMPLATE, tenantId != null ? tenantId : "common"),
                USER_INFO_URI);
        this.tenantId = tenantId != null ? tenantId : "common";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getClientName() {
        return CLIENT_NAME;
    }

    @Override
    public String getAuthorizationUri() {
        return String.format(AUTHORIZATION_URI_TEMPLATE, tenantId);
    }

    @Override
    public String getTokenUri() {
        return String.format(TOKEN_URI_TEMPLATE, tenantId);
    }

    @Override
    public String getUserInfoUri() {
        return USER_INFO_URI;
    }

    @Override
    public String getIssuer() {
        return String.format(ISSUER_TEMPLATE, tenantId);
    }

    @Override
    public Collection<String> getScopes() {
        Collection<String> scopes = super.getScopes();

        if (scopes == null || scopes.isEmpty()) {
            scopes = new ArrayList<>();
            scopes.add("openid");
            scopes.add("profile");
            scopes.add("email");
        }

        return scopes;
    }

    @Override
    public String toString() {
        return "AzureAD [tenantId="
                + tenantId
                + ", clientId="
                + getClientId()
                + ", clientSecret="
                + (getClientSecret() != null && !getClientSecret().isBlank() ? "*****" : "NULL")
                + ", scopes="
                + getScopes()
                + ", useAsUsername="
                + getUseAsUsername()
                + "]";
    }
}
