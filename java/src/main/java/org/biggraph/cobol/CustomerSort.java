package org.biggraph.cobol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

/** SORT1F USING/GIVING, including the CUSTOMER copybook's fixed-width key. */
public final class CustomerSort {
    private CustomerSort() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CustomerSort <input-file> <output-file>");
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
            || (Files.exists(output) && Files.isSameFile(input, output))) {
            throw new IllegalArgumentException("Input and output must be different files");
        }
        var records = new ArrayList<String>();
        for (String row : Files.readAllLines(input, StandardCharsets.ISO_8859_1)) {
            records.add((row + " ".repeat(157)).substring(0, 157));
        }
        records.sort(Comparator.comparing(row -> row.substring(16, 37)));
        // GnuCOBOL line-sequential WRITE removes trailing spaces.
        var lines = records.stream().map(row -> row.replaceFirst(" +$", "")).toList();
        Files.write(output, lines, StandardCharsets.ISO_8859_1);
        System.out.println("1000-Begin-Job: ");
        System.out.println("Normally, I would have something to do here");
        System.out.println("3000-End-Job: ");
        System.out.println("Normally, I would have something to do here");
    }
}
