# SUB01.cbl 1 Java translation (translations/Sub01Demo.java)

What this is
- A runnable Java prototype that implements the logical behavior of SUB01.cbl.
- Located as translations/Sub01Demo.java for quick testing.

Key mapping decisions
- Linkage items (LF-A .. LF-G PIC X(05)): represented as FieldHolder objects (mutable reference-like wrapper).
- Working-storage (CF-D .. CF-F): local FieldHolder variables in Sub01.run and passed to Sub03.
- MOVE semantics: values are padded/truncated to 5 characters to emulate PIC X(05).
- DISPLAY: mapped to System.out.println with ordering preserved.
- CALL 'SUB03' (static): Sub03.run(cfD, cfE, cfF) 2 a stub is provided; translate real SUB03 for full behavior.
- CALL G-PROG (dynamic): name-dispatch to Sub04.run(lf.lfG). For a more complete system, implement a ProgramRegistry.

How to integrate into repo (manual steps)
1. Create and switch to a branch locally:
   git fetch origin release
   git checkout -b feature/translate-sub01-java origin/release

2. Create folder and add files:
   mkdir -p translations
   (copy translations/Sub01Demo.java and translations/README.md into translations/)

3. Commit & push:
   git add translations/Sub01Demo.java translations/README.md
   git commit -m "Add Java translation of SUB01 (prototype) under translations/"
   git push origin feature/translate-sub01-java

4. Create a Pull Request from feature/translate-sub01-java into release on GitHub.

Notes, limitations & next steps
- Sub03 and Sub04 are stubbed. I can translate SUB03.cbl and SUB04.cbl into Java and replace stubs for fidelity.
- This prototype assumes ASCII and simple String semantics. If mainframe EBCDIC or binary fields are relevant, add encoding conversion utilities and fixed-byte record handling.
- Dynamic calls in COBOL can accept different signatures; for a robust translation you may need a ProgramRegistry that maps program names to implementations and enforces signatures.

Suggested next actions (pick one)
- I can translate SUB03.cbl and SUB04.cbl (I already have access; respond 2translate SUB03 and SUB042).
- I can produce a proper Java package layout (one class per file) and unit tests comparing expected field values.
- I can attempt to create the branch & PR again if you grant access, and open the PR for review.
