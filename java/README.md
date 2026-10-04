# Java migration

This is an in-progress translation, not a completed conversion of the repository.
The agreed scope is all 164 COBOL source files, including mainframe examples and
templates, with mainframe integration through z/OS Connect and OpenAPI.
The original COBOL programs and datasets are preserved.

## Implemented and verified

| COBOL source | Java class |
| --- | --- |
| `OpenCobol/prod/cbl/hello.cbl` | `org.biggraph.cobol.Hello` |
| `OpenCobol/prod/cbl/BDS0702.cbl` | `org.biggraph.cobol.EmployeeReport` |
| `OpenCobol/Internal-Sort/cbl/sort1f.cbl` | `org.biggraph.cobol.CustomerSort` |

Java 21 is required. From the repository root, run `bash java/build.sh`.
Build outputs default to `/workspace/cobol-java-build` outside the checkout.
Run the employee report with an explicit dataset path:

```sh
java -cp /workspace/cobol-java-build/classes org.biggraph.cobol.EmployeeReport common/data/c07-employee.dat.txt
```

Run the customer sort with explicit input and output paths:

```sh
java -cp /workspace/cobol-java-build/classes org.biggraph.cobol.CustomerSort common/data/customer.dat.txt /workspace/cobol-java-build/customers.txt
```

`bash java/verify.sh` compiles the three original COBOL programs using the
onboarding compiler, then compares their console and data output with Java.
It checks all three employee records and all 1,000 customer records.

## Outstanding work

`migration.csv` records every source independently; similar mainframe variants
are not marked translated simply because a local variant is implemented.
Remaining programs include reporting, payroll, table exercises, intrinsic
functions, subprogram calls, mainframe variants, and CICS templates.

The actual z/OS Connect OpenAPI document and server URL are required to define
service operations, payloads, authentication and deployment compatibility.
The word "OpenAPI" identifies the format but does not supply those definitions.
No mainframe calls have been implemented or tested. Credentials must be supplied
through secure environment settings, never committed to this project.
