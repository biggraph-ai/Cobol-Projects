package org.biggraph.cobol.mainframe;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.json.JSONObject;

/** Calls operations declared by the user's z/OS Connect OpenAPI JSON document. */
public final class ZosConnectClient {
    private record Operation(String method, String path) {}
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");
    private final URI base;
    private final HttpClient http;
    private final Supplier<String> authorization;
    private final Map<String, Operation> operations = new HashMap<>();

    public ZosConnectClient(URI base, Path openApiJson, Supplier<String> authorization) throws IOException {
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
            || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("Server must be an HTTP(S) base URL without embedded credentials, query or fragment");
        }
        this.base = URI.create(base.toString().replaceAll("/$", "") + "/");
        this.authorization = authorization;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        JSONObject document = new JSONObject(Files.readString(openApiJson));
        JSONObject paths = document.getJSONObject("paths");
        for (String path : paths.keySet()) {
            JSONObject item = paths.getJSONObject(path);
            for (String method : item.keySet()) {
                if (!METHODS.contains(method)) continue;
                JSONObject definition = item.getJSONObject(method);
                String id = definition.optString("operationId", "");
                if (id.isBlank()) continue;
                if (operations.putIfAbsent(id, new Operation(method.toUpperCase(Locale.ROOT), path)) != null) {
                    throw new IllegalArgumentException("Duplicate OpenAPI operationId: " + id);
                }
            }
        }
    }
    public Set<String> operationIds() { return Set.copyOf(operations.keySet()); }

    public String invoke(String operationId, Map<String, String> pathParameters,
                         Map<String, String> queryParameters, String jsonBody)
                         throws IOException, InterruptedException {
        Operation operation = operations.get(operationId);
        if (operation == null) throw new IllegalArgumentException("Unknown OpenAPI operationId: " + operationId);
        String path = operation.path();
        for (var parameter : pathParameters.entrySet()) {
            path = path.replace("{" + parameter.getKey() + "}", encode(parameter.getValue()));
        }
        if (path.contains("{") || path.contains("}")) throw new IllegalArgumentException("Missing OpenAPI path parameter");
        StringBuilder query = new StringBuilder();
        for (var parameter : queryParameters.entrySet()) {
            query.append(query.isEmpty() ? '?' : '&').append(encode(parameter.getKey()))
                .append('=').append(encode(parameter.getValue()));
        }
        URI endpoint = base.resolve(path.replaceFirst("^/", "") + query);
        if (!endpoint.getScheme().equals(base.getScheme()) || !endpoint.getAuthority().equals(base.getAuthority())
            || !endpoint.normalize().getPath().startsWith(base.getPath())) {
            throw new IllegalArgumentException("Operation URL escapes configured server base");
        }
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json");
        String auth = authorization == null ? null : authorization.get();
        if (auth != null && !auth.isBlank()) request.header("Authorization", auth);
        if (jsonBody != null) request.header("Content-Type", "application/json");
        request.method(operation.method(), jsonBody == null
            ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("z/OS Connect operation " + operationId + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }
    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
