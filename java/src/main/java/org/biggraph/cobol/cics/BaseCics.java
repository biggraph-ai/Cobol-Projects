package org.biggraph.cobol.cics;

/** Completes the BASECICS control-flow template with a typed presentation contract. */
public final class BaseCics {
    public enum Key { ENTER, PF3, OTHER }
    public record Request(boolean firstCall, Key key, boolean mapFail, WageCalculator.Input input) {}
    public record Response(String transactionId, String mapSet, String mapName,
                           String message, WageCalculator.Result wages) {}
    public Response handle(Request request) {
        if (request.firstCall() || request.key() == Key.PF3) {
            return response("ENTER VALUES, PRESS <ENTER> OR <CLEAR> TO QUIT", null);
        }
        if (request.key() != Key.ENTER) {
            return response("YOU PRESSED A WRONG KEY, PRESS <ENTER>,<CLEAR> TO QUIT", null);
        }
        if (request.mapFail()) return response("NO DATA ENTERED; ENTER DATA OR <CLEAR> TO QUIT", null);
        var wages = WageCalculator.calculate(request.input());
        return response(wages.errors().isEmpty()
            ? (wages.grossOverflow() ? "CANNOT DISPLAY GROSS-PAY, NUMBER TOO LARGE"
                                    : "WAGES COMPUTED, ENTER MORE DATA OR <CLEAR> TO QUIT")
            : "HIGHLIGHT FIELDS IN ERROR, CORRECT, PRESS <ENTER>", wages);
    }
    private Response response(String message, WageCalculator.Result result) {
        return new Response("$001", "$001M", "CALCMAP", message, result);
    }
}
