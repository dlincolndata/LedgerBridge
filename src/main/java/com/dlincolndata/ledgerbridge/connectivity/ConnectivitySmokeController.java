package com.dlincolndata.ledgerbridge.connectivity;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/connectivity")
@RequiredArgsConstructor
public class ConnectivitySmokeController {

    private final SmokeTestClients smokeTestClients;
    private final ConnectivityProperties props;

    @GetMapping("/smoke-test")
    public Map<String, Object> runSmokeTests() {
        List<SmokeTestResult> results = smokeTestClients.runAllSmokeTests();
        return Map.of(
                "connectivityMode", props.getMode(),
                "companyCode", props.getSap().getCompanyCode(),
                "checks", results
        );
    }
}
