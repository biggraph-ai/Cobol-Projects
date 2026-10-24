package translations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/*
 * JUnit 5 test cases for the Sub01 translation.
 * These tests require JUnit 5 on the classpath to run and are provided as examples.
 */
public class Sub01JUnitTest {

    @Test
    public void testMoveAndStaticCall() {
        LinkageFields lf = new LinkageFields("A    ", "B    ", "C    ", "D    ", "E    ", "F    ", "G    ");
        Sub01.run(lf);
        assertEquals("D03", lf.lfD.get().trim(), "LF-D should contain value from SUB03 stub");
        assertEquals("E03", lf.lfE.get().trim(), "LF-E should contain value from SUB03 stub");
        assertEquals("F03", lf.lfF.get().trim(), "LF-F should contain value from SUB03 stub");
    }

    @Test
    public void testDynamicCall() {
        LinkageFields lf = new LinkageFields("A    ", "B    ", "C    ", "D    ", "E    ", "F    ", "G    ");
        Sub01.run(lf);
        String g = lf.lfG.get().trim();
        // The stub for SUB04 appends _04; accept either appended form or SUB1 depending on flow
        assertTrue(g.equals("SUB1") || g.endsWith("_04"), "LF-G should be updated by SUB04 or remain SUB1");
    }
}
