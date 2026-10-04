package org.biggraph.cobol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** BDS0702: retain the fixed-width employee fields and displayed date format. */
public final class EmployeeReport {
    private EmployeeReport() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: EmployeeReport <employee-data-file>");
        }
        try (var rows = Files.lines(Path.of(args[0]), StandardCharsets.ISO_8859_1)) {
            rows.forEach(row -> {
                // COBOL pads short line-sequential records to the declared length.
                String record = (row + " ".repeat(43)).substring(0, 43);
                System.out.println(record.substring(24, 34) + " "
                    + record.substring(9, 24) + " - "
                    + record.substring(38, 40) + "/"
                    + record.substring(40, 42) + "/"
                    + record.substring(34, 38));
            });
        }
    }
}
