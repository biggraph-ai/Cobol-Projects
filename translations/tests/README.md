Tests for the SUB01 Java translation

This directory contains two kinds of test cases for the Sub01 translation:

1) Sub01PlainTest.java — a dependency-free test runner (plain Java) that you can compile/run without external libraries.
   Compile:
     javac translations/*.java
   Run:
     java translations.Sub01PlainTest

   Exit code: 0 = all tests passed, non-zero = failures.

2) Sub01JUnitTest.java — JUnit 5 test cases.
   These require JUnit 5 on the classpath. Example using Maven or a JUnit console runner is required.

Notes
- The tests are illustrative and may fail depending on stub behavior and environment. They are intentionally permissive in some assertions.
- If you want me to author a Maven pom.xml or Gradle build to run these tests in CI, I can add one to the branch.
