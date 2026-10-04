package org.biggraph.cobol.cics;

/** Java transaction model for CALCTEST; BMS presentation is an integration concern. */
public final class CalcTest {
    public enum Key { ENTER, CLEAR, OTHER }
    public record Request(boolean firstCall, Key key, boolean mapFail, WageCalculator.Input input) {}
    public record Response(boolean ended, String transactionId, String message,
                           String cursorField, WageCalculator.Result wages) {}
    public Response handle(Request request) {
        if (request.firstCall()) return prompt("ENTER VALUES, PRESS<ENTER>, <CLEAR> TO QUIT");
        if (request.key() == Key.CLEAR) return new Response(true, "", "QUITTING AS REQUESTED - USE CESF LOGOFF TO END CICS", "", null);
        if (request.mapFail()) return prompt("NO DATA ENTERED; ENTER DATA OR <CLEAR> TO QUIT");
        WageCalculator.Result result = WageCalculator.calculate(request.input());
        if (!result.errors().isEmpty()) return new Response(false, "W192",
            "HIGHLIGHT FIELDS IN ERROR, CORRECT, PRESS <ENTER>",
            result.errors().containsKey("firstName") ? "firstName" : result.errors().keySet().iterator().next(), result);
        return new Response(false, "W192", result.grossOverflow()
            ? "CANNOT DISPLAY GROSS-PAY, NUMBER TOO LARGE"
            : "WAGES COMPUTED, ENTER MORE DATA OR <CLEAR> TO QUIT", "", result);
    }
    private Response prompt(String message) { return new Response(false, "W192", message, "firstName", null); }
}
