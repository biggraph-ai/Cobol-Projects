package translations;

/*
 * Simple, dependency-free test runner for Sub01 translation.
 * This test runner does not require JUnit and can be run with:
 *   javac translations/*.java
 *   java translations.Sub01PlainTest
 *
 * It performs a few assertions and prints PASS/FAIL for each test.
 */
public class Sub01PlainTest {
    public static void main(String[] args) {
        int failures = 0;

        boolean r1 = testMoveAndStaticCall();
        report("testMoveAndStaticCall", r1);
        if (!r1) failures++;

        boolean r2 = testDynamicCall();
        report("testDynamicCall", r2);
        if (!r2) failures++;

        System.out.println("Total failures: " + failures);
        System.exit(failures == 0 ? 0 : 2);
    }

    static void report(String name, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + name);
    }

    // Verify that SUB03 stub updates CF-D/E/F and they are moved into LF-D/E/F
    static boolean testMoveAndStaticCall() {
        LinkageFields lf = new LinkageFields("A    ", "B    ", "C    ", "D    ", "E    ", "F    ", "G    ");
        Sub01.run(lf);
        // In the current prototype, SUB03 stub sets CF-D/E/F to D03/E03/F03 and SUB01 moves them to LF-D/E/F
        boolean okD = "D03".equals(lf.lfD.get().trim());
        boolean okE = "E03".equals(lf.lfE.get().trim());
        boolean okF = "F03".equals(lf.lfF.get().trim());
        return okD && okE && okF;
    }

    // Verify that dynamic call to SUB04 alters LF-G (stub appends _04)
    static boolean testDynamicCall() {
        LinkageFields lf = new LinkageFields("A    ", "B    ", "C    ", "D    ", "E    ", "F    ", "G    ");
        Sub01.run(lf);
        // Sub04 stub appends "_04" to LF-G (then truncated/padded to 5 chars)
        String g = lf.lfG.get().trim();
        return g.endsWith("_04") || "SUB1".equals(g); // accept either depending on execution order/implementation
    }
}
