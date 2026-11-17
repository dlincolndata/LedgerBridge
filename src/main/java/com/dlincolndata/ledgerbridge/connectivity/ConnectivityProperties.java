package com.dlincolndata.ledgerbridge.connectivity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ledgerbridge.connectivity")
public class ConnectivityProperties {
    private String mode = "SIMULATED";
    private SalesforceProps salesforce = new SalesforceProps();
    private SapProps sap = new SapProps();

    @Data
    public static class SalesforceProps {
        private String instanceUrl;
        private String clientId;
        private String clientSecret;
    }

    @Data
    public static class SapProps {
        private String baseUrl;
        private String companyCode;
        private String commUser;
        private String commPassword;
        private String sandboxApiKey;
    }
}
