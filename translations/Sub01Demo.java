/*
 * Translated from SUB01.cbl (COBOL) to Java.
 *
 * Notes:
 * - PIC X(05) fields are modeled as Strings wrapped in FieldHolder to simulate pass-by-reference.
 * - WORKING-STORAGE CF-D/CF-E/CF-F are local mutable holders passed to SUB03 (a stub here).
 * - Dynamic CALL (CALL G-PROG) is implemented as a simple name-dispatch to Sub04.run(...).
 *
 * To compile:
 *   javac translations/Sub01Demo.java
 * To run:
 *   java -cp . translations.Sub01Demo
 *
 * Place this file under translations/ in the repository.
 */

package translations;

class FieldHolder {
    private String value;
    public FieldHolder() { this("     "); } // default 5 spaces like PIC X(05)
    public FieldHolder(String v) { this.value = padTo5(v); }
    public String get() { return value; }
    public void set(String v) { this.value = padTo5(v); }
    private static String padTo5(String s) {
        if (s == null) s = "";
        if (s.length() == 5) return s;
        if (s.length() > 5) return s.substring(0,5);
        return String.format("%-5s", s); // right-pad with spaces
    }
    @Override
    public String toString() { return value; }
}

class LinkageFields {
    public final FieldHolder lfA = new FieldHolder();
    public final FieldHolder lfB = new FieldHolder();
    public final FieldHolder lfC = new FieldHolder();
    public final FieldHolder lfD = new FieldHolder();
    public final FieldHolder lfE = new FieldHolder();
    public final FieldHolder lfF = new FieldHolder();
    public final FieldHolder lfG = new FieldHolder();

    public LinkageFields() {}

    public LinkageFields(String a, String b, String c, String d, String e, String f, String g) {
        lfA.set(a); lfB.set(b); lfC.set(c); lfD.set(d); lfE.set(e); lfF.set(f); lfG.set(g);
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] [%s] [%s] [%s] [%s] [%s]",
            lfA.get(), lfB.get(), lfC.get(), lfD.get(), lfE.get(), lfF.get(), lfG.get());
    }

    public String debug() {
        return String.format("%s, %s, %s, %s, %s, %s, %s",
            lfA, lfB, lfC, lfD, lfE, lfF, lfG);
    }
}

/* Stub for SUB03 (called statically by SUB01)
 * COBOL: CALL 'SUB03' USING CF-D, CF-E, CF-F.
 * Java: modify FieldHolder instances to emulate mutation by callee.
 */
class Sub03 {
    public static void run(FieldHolder cfD, FieldHolder cfE, FieldHolder cfF) {
        System.out.println("[SUB03] start - received CF-D/E/F: [" + cfD + "] [" + cfE + "] [" + cfF + "]");
        // Example stub behavior: set identifiable values
        cfD.set("D03");
        cfE.set("E03");
        cfF.set("F03");
        System.out.println("[SUB03] end   - updated CF-D/E/F: [" + cfD + "] [" + cfE + "] [" + cfF + "]");
    }
}

/* Stub for SUB04 (called dynamically by SUB01).
 * COBOL: CALL G-PROG USING LF-G where G-PROG = "SUB04"
 */
class Sub04 {
    public static void run(FieldHolder lfG) {
        System.out.println("[SUB04] start - received LF-G: [" + lfG + "]");
        String current = lfG.get().trim();
        lfG.set((current + "_04").substring(0, Math.min(5, current.length()+3)));
        System.out.println("[SUB04] end   - updated LF-G: [" + lfG + "]");
    }
}

/* Main translation of SUB01.
 * COBOL signature: PROCEDURE DIVISION USING LF-A..LF-G.
 */
class Sub01 {
    public static void run(LinkageFields lf) {
        // WORKING-STORAGE: CF-D, CF-E, CF-F
        FieldHolder cfD = new FieldHolder();
        FieldHolder cfE = new FieldHolder();
        FieldHolder cfF = new FieldHolder();

        // MOVE 'SUB1' TO LF-A..LF-G.
        lf.lfA.set("SUB1");
        lf.lfB.set("SUB1");
        lf.lfC.set("SUB1");
        lf.lfD.set("SUB1");
        lf.lfE.set("SUB1");
        lf.lfF.set("SUB1");
        lf.lfG.set("SUB1");

        System.out.println("In SUB01:Before static call: " + lf.debug());

        // CALL 'SUB03' USING CF-D, CF-E, CF-F.
        Sub03.run(cfD, cfE, cfF);

        System.out.println("In SUB01:Before static call - Link fields: " + lf.debug());
        System.out.println("In SUB01: After static call WS fields: [" + cfD + "] [" + cfE + "] [" + cfF + "]");

        // MOVE CF-D/E/F TO LF-D/E/F
        lf.lfD.set(cfD.get());
        lf.lfE.set(cfE.get());
        lf.lfF.set(cfF.get());

        System.out.println("In SUB01:After static call - After MOVE: " + lf.debug());

        // MOVE "SUB04" to G-PROG and dynamic CALL G-PROG USING LF-G.
        String gProg = "SUB04";

        // dispatch
        if ("SUB04".equalsIgnoreCase(gProg)) {
            Sub04.run(lf.lfG);
        } else if ("SUB03".equalsIgnoreCase(gProg)) {
            Sub03.run(cfD, cfE, cfF); // signature mismatch example only
        } else {
            System.out.println("Unknown dynamic program: " + gProg + " (no-op)");
        }

        System.out.println("In SUB01:After dynamic call: " + lf.debug());

        // GOBACK -> return
    }
}

/* Demo driver that simulates MAINPGM calling SUB01 with linkage parameters */
public class Sub01Demo {
    public static void main(String[] args) {
        LinkageFields lf = new LinkageFields("AAA  ","BBB  ","CCC  ","DDD  ","EEE  ","FFF  ","GGG  ");
        System.out.println("Before SUB01 call (linkage): " + lf.debug());

        Sub01.run(lf);

        System.out.println("After SUB01 call (linkage):  " + lf.debug());
    }
}
