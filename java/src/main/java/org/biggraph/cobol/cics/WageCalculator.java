package org.biggraph.cobol.cics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** The validation and pay arithmetic in CALCTEST and BASECICS. */
public final class WageCalculator {
    private WageCalculator() {}
    public record Input(String firstName, String lastName, String hourlyWage, String hoursWorked) {}
    public record Result(BigDecimal basePay, BigDecimal overtime, BigDecimal grossPay,
                         Map<String, String> errors, boolean grossOverflow) {}

    public static Result calculate(Input input) {
        Map<String, String> errors = new LinkedHashMap<>();
        BigDecimal wage = positive(input.hourlyWage(), "hourlyWage", "HOURLY WAGE", errors);
        BigDecimal hours = positive(input.hoursWorked(), "hoursWorked", "NUMBER OF HOURS", errors);
        if (input.lastName() == null || input.lastName().isBlank()) errors.put("lastName", "YOU MUST ENTER A LAST NAME");
        if (input.firstName() == null || input.firstName().isBlank()) errors.put("firstName", "YOU MUST ENTER A FIRST NAME");
        if (!errors.isEmpty()) return new Result(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Map.copyOf(errors), false);
        BigDecimal forty = new BigDecimal("40");
        BigDecimal base = assign(hours.min(forty).multiply(wage));
        BigDecimal overtime = hours.compareTo(forty) > 0
            ? assign(hours.subtract(forty).multiply(new BigDecimal("1.5")).multiply(wage))
            : new BigDecimal("0.00");
        BigDecimal gross = base.add(overtime).setScale(2, RoundingMode.DOWN);
        // The supplied map copybook is missing; use the working-storage S9(4)V99
        // range for the Java API, and report overflow rather than invent a map layout.
        boolean overflow = gross.abs().compareTo(new BigDecimal("9999.99")) > 0;
        return new Result(base, overtime, overflow ? new BigDecimal("0.00") : gross, Map.of(), overflow);
    }
    private static BigDecimal assign(BigDecimal value) {
        // COMPUTE into signed S9(4)V99 without ROUNDED truncates fractional cents
        // and high-order digits if no ON SIZE ERROR handler is present.
        return value.setScale(2, RoundingMode.DOWN).remainder(new BigDecimal("10000"));
    }
    private static BigDecimal positive(String input, String field, String label, Map<String, String> errors) {
        if (input == null || input.isBlank()) {
            errors.put(field, "YOU MUST ENTER " + label); return BigDecimal.ZERO;
        }
        if (!input.strip().matches("[+]?[0-9]+(?:\\.[0-9]+)?")) {
            errors.put(field, label + " MUST BE NUMERIC"); return BigDecimal.ZERO;
        }
        BigDecimal value = new BigDecimal(input.strip());
        if (value.signum() <= 0) errors.put(field, label + " MUST BE POSITIVE");
        return value;
    }
}
