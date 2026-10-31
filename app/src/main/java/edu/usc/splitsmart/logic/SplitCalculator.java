package edu.usc.splitsmart.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.model.SplitType;

/** Pure split math (R9-R11). Input and output preserve participant insertion order. */
public final class SplitCalculator {
    private static final long BASIS_POINTS = 10000;

    private SplitCalculator() {
    }

    /**
     * Returns cents owed by each participant. Equal splits give leftover pennies to the first
     * participants. Percentage splits use largest fractional remainders, with ties resolved in
     * participant order; a 0% participant never receives a rounding penny. No floating point or
     * overflowing total-times-percentage multiplication is used, even for Long.MAX_VALUE.
     * Equal allocation values are ignored; other allocation values must be nonnegative.
     */
    public static Map<String, Long> calculate(long totalCents, SplitType type,
                                               Map<String, Long> allocations)
            throws SplitSmartException {
        if (totalCents <= 0) {
            throw new SplitSmartException(SplitSmartException.Code.INVALID_AMOUNT);
        }
        if (type == null || allocations == null || allocations.isEmpty()) {
            throw invalidSplit();
        }
        for (String id : allocations.keySet()) {
            if (id == null || id.trim().isEmpty()) {
                throw invalidSplit();
            }
        }

        Map<String, Long> shares = new LinkedHashMap<>();
        if (type == SplitType.EQUAL) {
            long base = totalCents / allocations.size();
            long extra = totalCents % allocations.size();
            for (String id : allocations.keySet()) {
                shares.put(id, base + (extra-- > 0 ? 1 : 0));
            }
            return shares;
        }

        // Subtract from a bounded remainder instead of summing untrusted values.
        long remaining = type == SplitType.PERCENTAGE ? BASIS_POINTS : totalCents;
        for (Long value : allocations.values()) {
            if (value == null || value < 0 || value > remaining) {
                throw invalidSplit();
            }
            remaining -= value;
        }
        if (remaining != 0) {
            throw invalidSplit();
        }
        if (type == SplitType.EXACT_AMOUNT) {
            shares.putAll(allocations);
            return shares;
        }

        Map<String, Long> remainders = new LinkedHashMap<>();
        long unassigned = totalCents;
        for (Map.Entry<String, Long> entry : allocations.entrySet()) {
            long fractional = (totalCents % BASIS_POINTS) * entry.getValue();
            long cents = (totalCents / BASIS_POINTS) * entry.getValue()
                    + fractional / BASIS_POINTS;
            shares.put(entry.getKey(), cents);
            remainders.put(entry.getKey(), fractional % BASIS_POINTS);
            unassigned -= cents;
        }
        List<String> roundingOrder = new ArrayList<>(shares.keySet());
        // List.sort is stable, so equal remainders retain participant order.
        roundingOrder.sort((a, b) -> Long.compare(remainders.get(b), remainders.get(a)));
        for (int i = 0; i < unassigned; i++) {
            String id = roundingOrder.get(i);
            shares.put(id, shares.get(id) + 1);
        }
        return shares;
    }

    private static SplitSmartException invalidSplit() {
        return new SplitSmartException(SplitSmartException.Code.INVALID_SPLIT);
    }
}
