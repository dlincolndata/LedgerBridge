package com.dlincolndata.ledgerbridge.connectivity;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmokeTestClients {

    private final ConnectivityProperties props;
    private final RestClient restClient = RestClient.create();

    public List<SmokeTestResult> runAllSmokeTests() {
        return List.of(
                testSalesforceAuth(),
                testSapScenario("SAP_COM_0008", "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_BusinessPartner?$top=1&$format=json"),
                testSapScenario("SAP_COM_0176", "/sap/opu/odata/sap/API_BILLING_DOCUMENT_REQUEST_SRV/$metadata"),
                testSapScenario("SAP_COM_0120", "/sap/opu/odata/sap/API_BILLING_DOCUMENT_SRV/A_BillingDocument?$top=1&$format=json")
        );
    }

    private SmokeTestResult testSalesforceAuth() {
        String endpoint = props.getSalesforce().getInstanceUrl() + "/services/oauth2/token";
        Map<String, String> redactedHeaders = Map.of(
                "Content-Type", "application/x-www-form-urlencoded",
                "client_id", mask(props.getSalesforce().getClientId()),
                "client_secret", "***REDACTED***"
        );

        if (!"LIVE".equalsIgnoreCase(props.getMode())) {
            return new SmokeTestResult(
                    "SALESFORCE",
                    "OAUTH2_CLIENT_CREDENTIALS",
                    "SIMULATED",
                    200,
                    endpoint,
                    "CONNECTIVITY_MODE=SIMULATED (Live Salesforce Connected App credentials not bound)",
                    redactedHeaders,
                    Map.of("access_token", "***REDACTED***", "token_type", "Bearer", "instance_url", props.getSalesforce().getInstanceUrl()),
                    Instant.now()
            );
        }

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", props.getSalesforce().getClientId());
            form.add("client_secret", props.getSalesforce().getClientSecret());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> sanitizedBody = new LinkedHashMap<>();
            if (response != null) {
                response.forEach((k, v) -> sanitizedBody.put(k, k.toLowerCase().contains("token") ? "***REDACTED***" : v));
            }

            return new SmokeTestResult(
                    "SALESFORCE",
                    "OAUTH2_CLIENT_CREDENTIALS",
                    "SUCCESS",
                    200,
                    endpoint,
                    null,
                    redactedHeaders,
                    sanitizedBody,
                    Instant.now()
            );
        } catch (RestClientResponseException ex) {
            return new SmokeTestResult(
                    "SALESFORCE",
                    "OAUTH2_CLIENT_CREDENTIALS",
                    "BLOCKED",
                    ex.getStatusCode().value(),
                    endpoint,
                    "Salesforce OAuth failure: " + ex.getStatusText(),
                    redactedHeaders,
                    ex.getResponseBodyAsString(),
                    Instant.now()
            );
        } catch (Exception ex) {
            return new SmokeTestResult(
                    "SALESFORCE",
                    "OAUTH2_CLIENT_CREDENTIALS",
                    "BLOCKED",
                    503,
                    endpoint,
                    "Connection error: " + ex.getMessage(),
                    redactedHeaders,
                    null,
                    Instant.now()
            );
        }
    }

    private SmokeTestResult testSapScenario(String scenario, String path) {
        String endpoint = props.getSap().getBaseUrl() + path;
        Map<String, String> redactedHeaders = Map.of(
                "Authorization", "Basic ***REDACTED***",
                "APIKey", props.getSap().getSandboxApiKey() != null && !props.getSap().getSandboxApiKey().isBlank() ? "***REDACTED***" : "NONE",
                "X-Company-Code", props.getSap().getCompanyCode()
        );

        if (!"LIVE".equalsIgnoreCase(props.getMode())) {
            Object simulatedPayload = switch (scenario) {
                case "SAP_COM_0008" -> Map.of("d", Map.of("results", List.of(
                        Map.of("BusinessPartner", "1000142", "BusinessPartnerFullName", "Acme Enterprise Corp", "CompanyCode", props.getSap().getCompanyCode())
                )));
                case "SAP_COM_0176" -> Map.of("service", "API_BILLING_DOCUMENT_REQUEST_SRV", "status", "METADATA_CONTRACT_VERIFIED");
                default -> Map.of("d", Map.of("results", List.of(
                        Map.of("BillingDocument", "90000481", "CompanyCode", props.getSap().getCompanyCode(), "TransactionCurrency", "USD")
                )));
            };

            return new SmokeTestResult(
                    "SAP_S4HANA_CLOUD",
                    scenario,
                    "SIMULATED",
                    200,
                    endpoint,
                    "CONNECTIVITY_MODE=SIMULATED (Blocked: SAP Student License lacks inbound Communication Arrangement provisioning)",
                    redactedHeaders,
                    simulatedPayload,
                    Instant.now()
            );
        }

        try {
            String rawCreds = props.getSap().getCommUser() + ":" + props.getSap().getCommPassword();
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(rawCreds.getBytes(StandardCharsets.UTF_8));

            var requestSpec = restClient.get()
                    .uri(endpoint)
                    .header("Authorization", basicAuth)
                    .accept(MediaType.APPLICATION_JSON);

            if (props.getSap().getSandboxApiKey() != null && !props.getSap().getSandboxApiKey().isBlank()) {
                requestSpec.header("APIKey", props.getSap().getSandboxApiKey());
            }

            String body = requestSpec.retrieve().body(String.class);

            return new SmokeTestResult(
                    "SAP_S4HANA_CLOUD",
                    scenario,
                    "SUCCESS",
                    200,
                    endpoint,
                    null,
                    redactedHeaders,
                    body,
                    Instant.now()
            );
        } catch (RestClientResponseException ex) {
            String preciseBlocker = switch (ex.getStatusCode().value()) {
                case 401 -> "401 Unauthorized: Invalid Communication User (" + props.getSap().getCommUser() + ") or credentials";
                case 403 -> "403 Forbidden: Missing authorization object or inactive Communication Arrangement for " + scenario;
                case 404 -> "404 Not Found: OData ICF node not activated for " + scenario;
                default -> "HTTP " + ex.getStatusCode().value() + ": " + ex.getStatusText();
            };

            return new SmokeTestResult(
                    "SAP_S4HANA_CLOUD",
                    scenario,
                    "BLOCKED",
                    ex.getStatusCode().value(),
                    endpoint,
                    preciseBlocker,
                    redactedHeaders,
                    ex.getResponseBodyAsString(),
                    Instant.now()
            );
        } catch (Exception ex) {
            return new SmokeTestResult(
                    "SAP_S4HANA_CLOUD",
                    scenario,
                    "BLOCKED",
                    503,
                    endpoint,
                    "SAP connectivity unreachable: " + ex.getMessage(),
                    redactedHeaders,
                    null,
                    Instant.now()
            );
        }
    }

    private String mask(String value) {
        if (value == null || value.length() <= 4) return "***REDACTED***";
        return value.substring(0, 4) + "***REDACTED***";
    }
}
