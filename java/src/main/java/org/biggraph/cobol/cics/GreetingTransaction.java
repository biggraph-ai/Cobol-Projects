package org.biggraph.cobol.cics;

/** SEND / RECEIVE / SEND / RETURN in slt_base_ibm_cics.cob. */
public final class GreetingTransaction {
    public interface Terminal {
        void send(String message, boolean erase);
        String receive();
        void end();
    }
    public void run(Terminal terminal) {
        terminal.send("What is your name? ", true);
        String input = terminal.receive();
        String name = ((input == null ? "" : input) + " ".repeat(20)).substring(0, 20);
        terminal.send("Hello " + name, true);
        terminal.end();
    }
}
