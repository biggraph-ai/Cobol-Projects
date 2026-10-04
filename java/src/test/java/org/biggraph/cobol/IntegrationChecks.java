package org.biggraph.cobol;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.biggraph.cobol.cics.BaseCics;
import org.biggraph.cobol.cics.CalcTest;
import org.biggraph.cobol.cics.GreetingTransaction;
import org.biggraph.cobol.cics.WageCalculator;
import org.biggraph.cobol.mainframe.ZosConnectClient;

public final class IntegrationChecks {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var regular = WageCalculator.calculate(new WageCalculator.Input("A", "B", "20", "40"));
        check(regular.grossPay().toPlainString().equals("800.00"), "regular pay");
        var overtime = WageCalculator.calculate(new WageCalculator.Input("A", "B", "20", "50"));
        check(overtime.basePay().toPlainString().equals("800.00")
            && overtime.overtime().toPlainString().equals("300.00")
            && overtime.grossPay().toPlainString().equals("1100.00"), "overtime calculation");
        var invalid = WageCalculator.calculate(new WageCalculator.Input("", "", "bad", "0"));
        check(invalid.errors().size() == 4, "all invalid fields reported");
        var truncated = WageCalculator.calculate(new WageCalculator.Input("A", "B", "10.123", "1"));
        check(truncated.grossPay().toPlainString().equals("10.12"), "COBOL truncation");
        var calc = new CalcTest();
        check(calc.handle(new CalcTest.Request(true, CalcTest.Key.ENTER, false, null)).transactionId().equals("W192"), "initial transaction");
        check(calc.handle(new CalcTest.Request(false, CalcTest.Key.CLEAR, false, null)).ended(), "clear ends transaction");
        check(calc.handle(new CalcTest.Request(false, CalcTest.Key.ENTER, true, null)).message().startsWith("NO DATA"), "map failure");
        check(new BaseCics().handle(new BaseCics.Request(false, BaseCics.Key.OTHER, false, null)).message().startsWith("YOU PRESSED"), "invalid key");
        List<String> messages = new ArrayList<>();
        new GreetingTransaction().run(new GreetingTransaction.Terminal() {
            public void send(String message, boolean erase) { messages.add(message); }
            public String receive() { return "Ada"; }
            public void end() { messages.add("END"); }
        });
        check(messages.equals(List.of("What is your name? ", "Hello Ada                 ", "END")), "greeting SEND/RECEIVE/RETURN");

        var document = Files.createTempFile("zos-connect-test-", ".json");
        Files.writeString(document, """
            {"openapi":"3.0.3","paths":{"/accounts/{id}":{"post":{"operationId":"lookup"}},
            "/error":{"get":{"operationId":"error"}}}}
            """);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/accounts/42", exchange -> {
            boolean correct = exchange.getRequestMethod().equals("POST")
                && exchange.getRequestURI().getRawQuery().equals("kind=a%20b")
                && exchange.getRequestHeaders().getFirst("Authorization").equals("Bearer fixture")
                && new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).equals("{\"name\":\"Ada\"}");
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(correct ? 200 : 400, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/error", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            var client = new ZosConnectClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"), document, () -> "Bearer fixture");
            check(client.invoke("lookup", Map.of("id", "42"), Map.of("kind", "a b"), "{\"name\":\"Ada\"}").equals("{\"ok\":true}"), "OpenAPI HTTP request and response");
            try { client.invoke("lookup", Map.of(), Map.of(), null); throw new AssertionError("missing path accepted"); }
            catch (IllegalArgumentException expected) { }
            try { client.invoke("error", Map.of(), Map.of(), null); throw new AssertionError("HTTP error accepted"); }
            catch (java.io.IOException expected) { check(expected.getMessage().contains("503"), "status preserved"); }
        } finally { server.stop(0); Files.deleteIfExists(document); }
        System.out.println("PASS: 12 CICS model and OpenAPI transport checks (local fixture server).");
    }
}
