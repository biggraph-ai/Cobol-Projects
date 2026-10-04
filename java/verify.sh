#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
source /workspace/toolchains/gnucobol/activate.sh
bash java/build.sh
check_dir=$(mktemp -d /workspace/cobol-java-check.XXXXXX)
trap 'rm -rf "$check_dir"' EXIT
mkdir -p "$check_dir/sort/jcl" "$check_dir/sort/data" "$check_dir/sort/spool"
cobc -x OpenCobol/prod/cbl/hello.cbl -o "$check_dir/hello"
cobc -x OpenCobol/prod/cbl/BDS0702.cbl -o "$check_dir/employees"
cobc -x -I common/cpy OpenCobol/Internal-Sort/cbl/sort1f.cbl -o "$check_dir/sort1f"
"$check_dir/hello" > "$check_dir/cobol-hello.txt"
java -cp /workspace/cobol-java-build/classes org.biggraph.cobol.Hello > "$check_dir/java-hello.txt"
cmp "$check_dir/cobol-hello.txt" "$check_dir/java-hello.txt"
(cd OpenCobol/prod/jcl && "$check_dir/employees") > "$check_dir/cobol-employees.txt"
java -cp /workspace/cobol-java-build/classes org.biggraph.cobol.EmployeeReport common/data/c07-employee.dat.txt > "$check_dir/java-employees.txt"
cmp "$check_dir/cobol-employees.txt" "$check_dir/java-employees.txt"
cp common/data/customer.dat.txt "$check_dir/sort/data/"
(cd "$check_dir/sort/jcl" && "$check_dir/sort1f") > "$check_dir/cobol-sort.txt"
java -cp /workspace/cobol-java-build/classes org.biggraph.cobol.CustomerSort common/data/customer.dat.txt "$check_dir/java-customers.txt" > "$check_dir/java-sort.txt"
cmp "$check_dir/cobol-sort.txt" "$check_dir/java-sort.txt"
cmp "$check_dir/sort/spool/cust-sort1f-out.txt" "$check_dir/java-customers.txt"
echo 'PASS: Java and COBOL outputs match for Hello, BDS0702, and SORT1F.'
