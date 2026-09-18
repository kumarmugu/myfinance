package com.myfinance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches an IBKR "Activity Flex" statement via the Interactive Brokers Flex Web Service.
 *
 * <p>The Flex Web Service is a two-step protocol:
 * <ol>
 *   <li><b>SendRequest</b> — submit the token + Flex Query id; IBKR returns a short-lived
 *       <em>reference code</em>.</li>
 *   <li><b>GetStatement</b> — poll with the token + reference code; IBKR returns the statement
 *       XML (generation takes a few seconds, so we retry on the "in progress" warning).</li>
 * </ol>
 *
 * <p><b>Security:</b> the token is supplied per-request by the user and is used only to build the
 * outbound IBKR URLs. It is never persisted, never written to a config file, and never logged
 * (only the reference code, which is useless without the token, appears in logs). This is the one
 * outbound network call the app makes, and it goes only to Interactive Brokers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IbkrFlexService {

    private static final String BASE =
            "https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService";
    private static final int MAX_POLLS = 12;                 // ~ up to ~1 min total
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern REF_CODE = Pattern.compile("<ReferenceCode>(\\d+)</ReferenceCode>");
    private static final Pattern STATUS = Pattern.compile("<Status>([^<]+)</Status>");
    private static final Pattern ERROR_MSG = Pattern.compile("<ErrorMessage>([^<]+)</ErrorMessage>");

    /**
     * Run the full Flex fetch and return the raw statement XML.
     *
     * @param token   the user's Flex Web Service token (transient — never stored)
     * @param queryId the Flex Query id configured in IBKR Client Portal
     */
    public String fetchStatementXml(String token, String queryId) {
        if (token == null || token.isBlank() || queryId == null || queryId.isBlank()) {
            throw new RuntimeException("IBKR Flex token and Query ID are both required");
        }
        String refCode = sendRequest(token.trim(), queryId.trim());
        return getStatement(token.trim(), refCode);
    }

    /** Step 1: submit the query, get a reference code. */
    private String sendRequest(String token, String queryId) {
        String url = BASE + "/SendRequest?t=" + enc(token) + "&q=" + enc(queryId) + "&v=3";
        String body = get(url);
        // A failed SendRequest returns <Status>Fail</Status> with an <ErrorMessage>.
        if (isFail(body)) {
            throw new RuntimeException("IBKR rejected the Flex request: " + errorMessage(body));
        }
        Matcher m = REF_CODE.matcher(body);
        if (!m.find()) {
            throw new RuntimeException("IBKR Flex did not return a reference code: " + errorMessage(body));
        }
        log.info("IBKR Flex SendRequest ok, reference code obtained (query {})", queryId);
        return m.group(1);
    }

    /** Step 2: poll for the generated statement. */
    private String getStatement(String token, String refCode) {
        String url = BASE + "/GetStatement?t=" + enc(token) + "&q=" + enc(refCode) + "&v=3";
        for (int attempt = 1; attempt <= MAX_POLLS; attempt++) {
            String body = get(url);
            // While generating, IBKR returns a <Status>Warn</Status> with a "try again" message.
            if (looksLikeStatement(body)) {
                log.info("IBKR Flex statement retrieved (reference {})", refCode);
                return body;
            }
            if (isFail(body) && !isInProgress(body)) {
                throw new RuntimeException("IBKR Flex statement error: " + errorMessage(body));
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for the IBKR statement");
            }
        }
        throw new RuntimeException("Timed out waiting for IBKR to generate the statement");
    }

    private String get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "MyFinance/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("IBKR Flex returned HTTP " + resp.statusCode());
            }
            return resp.body() == null ? "" : resp.body();
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Could not reach the IBKR Flex service: " + e.getMessage(), e);
        }
    }

    private boolean looksLikeStatement(String body) {
        // The generated statement is a FlexQueryResponse containing FlexStatements; the control
        // responses are <FlexStatementResponse> with a Status. Treat presence of statement data as success.
        return body.contains("<FlexQueryResponse") || body.contains("<FlexStatements");
    }

    private boolean isFail(String body) {
        Matcher m = STATUS.matcher(body);
        return m.find() && m.group(1).equalsIgnoreCase("Fail");
    }

    private boolean isInProgress(String body) {
        String lower = body.toLowerCase();
        // IBKR uses codes/messages like "statement generation in progress" while building the report.
        return lower.contains("generation in progress") || lower.contains("try again")
                || lower.contains("please try again");
    }

    private String errorMessage(String body) {
        Matcher m = ERROR_MSG.matcher(body);
        if (m.find()) return m.group(1);
        return "unknown error";
    }

    private String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }
}
