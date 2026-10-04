# Java conversion

The repository's COBOL programs now have Java implementations alongside their
originals. The conversion inventory covers all **164** source files:

| Source category | Count | Result |
| --- | ---: | --- |
| Nonempty batch and learning programs | 148 | Generated Java, compiled together |
| CICS templates | 3 | Java transaction models and shared wage calculation |
| Empty source files | 13 | Recorded as empty; no program to convert |

The batch programs run on the JVM using the Java `libcobj` runtime. Java source
is checked in under `generated/`; the original COBOL files are retained for
reference and behavioral comparison. Each original source path has its own Java
package, preserving different workshop versions with the same PROGRAM-ID.
`migration.csv` maps every original source to its Java class. `conversion.json`
also records source hashes, adaptations, and integration requirements.

## Build and run

Use JDK 21, Python 3, Git and curl. The default cloud locations are
`/workspace/cobol-java-build` for outputs and `/workspace/toolchains/cobol4j` for
the runtime. Set `JAVA_BUILD_DIR`, `COBOL4J_HOME` and `JAVA_HOME` to use other
locations. Generated build files and dependencies stay outside the checkout.

```sh
bash java/build.sh
```

The first build fetches pinned runtime source and checksum-verified dependencies,
then builds the runtime JAR. Subsequent builds reuse it. The runtime source is
pinned to opensource COBOL 4J 2.1.0, commit
`f46b43d1b50f47802f2d5c833f79202fb053d2ad`.

The Java command-line runner lists all source entries:

```sh
java -cp '/workspace/cobol-java-build/classes:/workspace/toolchains/cobol4j/lib/*' \
  org.biggraph.cobol.ProgramRunner --list
```

Run a program by its exact original repository path:

```sh
java -cp '/workspace/cobol-java-build/classes:/workspace/toolchains/cobol4j/lib/*' \
  org.biggraph.cobol.ProgramRunner OpenCobol/prod/cbl/hello.cbl
```

Programs keep their declared file paths and record formats. For the employee
example, run from its original JCL working directory:

```sh
cd OpenCobol/prod/jcl
java -cp '/workspace/cobol-java-build/classes:/workspace/toolchains/cobol4j/lib/*' \
  org.biggraph.cobol.ProgramRunner OpenCobol/prod/cbl/BDS0702.cbl
```

For programs with external dataset names, supply explicit bindings using repeated
`--dd NAME=PATH` options. For example, FAVRPT2 takes `FAVIN` and `FAVOUT`; TABLES00
takes `EMPFILE` and `PHONEOUT`. Use output paths outside the checkout. A dataset
name such as `UT-S-EMPFILE` is also accepted by the Java runner and bound to the
compiler's shortened name. Sequential mainframe-style inputs require fixed-length
records, including the declared packed-decimal fields. Line-sequential programs
read text lines. The verification tool demonstrates how to prepare fixed-record
fixtures without changing the originals.

Run subprograms through their callers or generated Java `execute`/`run` APIs.
The runner scopes subprogram lookup to the selected source family. To pass
command-line arguments to a program, place them after `--`.

`run.py` remains available when an explicit `--cwd` is convenient; program
execution uses Java. The initial hand-written `Hello`, `EmployeeReport` and
`CustomerSort` examples are retained.

## Validation

```sh
bash java/check.sh
```

This builds the Java solution and runs **12** checks of wage calculation,
transaction flow, input validation, and OpenAPI HTTP requests against a local
fixture server. The fixture server does not represent a live IBM deployment.

With the onboarding GnuCOBOL compiler activated:

```sh
source /workspace/toolchains/gnucobol/activate.sh
python3 java/tools/verify_generated.py
bash java/verify.sh
```

The differential suite compiles native reference programs and compares console
output and resulting files with Java in isolated copies of the input tree.
Current results in `validation.json` are **60 passed**, **9 passed with prepared
fixtures**, and **79 unrun**, with no unexplained differences or execution
failures in the executed checks. The initial three-program check also passes.

The 79 unrun entries comprise 41 programs requiring mainframe dataset bindings,
23 time-dependent or random demonstrations, 11 subprograms checked through
callers where available, and 4 programs with missing repository input fixtures.
All 148 batch programs are compiled; compilation and behavioral verification are
recorded separately.

Prepared fixtures include fixed-record employee, student, hospital and music
files, and a synthetic account containing valid packed decimals. The supplied
employee-project data has an invalid short record on line 13; that particular
comparison uses a separately prepared valid record. The repository's data is
unchanged. References that need syntax repairs are identified as adapted COBOL
in the validation report.

## CICS and z/OS Connect

The three CICS sources are translated into typed Java models:

- `CalcTest` provides the W192 initial, input, map-failure, calculation and clear
  flows.
- `BaseCics` provides the original template's initial, PF3, ENTER and invalid-key
  flows.
- `GreetingTransaction` implements SEND, RECEIVE, greeting and RETURN through a
  terminal interface.

`WageCalculator` implements regular pay, time-and-a-half overtime, validation,
cent truncation, and the working-storage signed S9(4)V99 range. The source wage
map copybook is missing, so the Java API explicitly uses that range for gross-pay
overflow; the real BMS output layout still needs binding. These are Java models
of incomplete templates, rather than claims of equivalent live CICS execution.

`ZosConnectClient` loads an **OpenAPI JSON** document, resolves operation IDs and
path/query parameters, sends JSON requests, accepts an authorization-header
supplier, and reports HTTP errors. For example, application code can construct
it with the real server URI and specification path, then call `invoke` with an
operation ID declared by that document. Request and response bodies must follow
the actual service schema. The supplied specification must contain concrete path
operations and operation IDs; external references and YAML need resolution or a
JSON export before loading.

Live integration is still outstanding. It requires the actual OpenAPI document,
server URL, authentication configuration, and CICS map/dataset mappings. No
service endpoints or payload contracts were invented. Store credentials in secure
environment settings. Mainframe JCL and Zowe deployment scripts remain as
reference material; the Java runner replaces the local compilation/run workflow.

## Regenerate and audit

Regeneration additionally requires a C compiler, make and GMP development headers.

```sh
python3 java/tools/bootstrap.py --compiler
python3 java/tools/generate.py
bash java/build.sh
```

The generator uses temporary conversion copies. Adaptations include explicit
array occurrence arguments for intrinsic `ALL` subscripts, case-compatible
copybook lookup, syntax repairs, the MAIN-to-MAINPGM rename, and a duplicate group
name repair. Each source's adaptations are recorded in `conversion.json`.

`CobolCompatibility` implements the runtime differences found by comparison:
subprogram initialization, table-sort key offsets, blank numeric values, DISPLAY
presentation, standard-output file writes, report advancing and final record
termination. A small package bridge accesses the runtime's package-private file
handle. Upstream runtime source remains unchanged.

See `THIRD_PARTY.md` and `tools/dependencies.lock.json` for dependency provenance
and licenses.
