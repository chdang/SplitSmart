package edu.usc.splitsmart.logic;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import edu.usc.splitsmart.model.SplitType;

public class SplitCalculatorTest {
    @Test
    public void equalGivesFirstParticipantExtraPenny() throws Exception {
        Map<String, Long> shares = SplitCalculator.calculate(1000, SplitType.EQUAL,
                allocations(0, 0, 0));
        assertEquals(Arrays.asList(334L, 333L, 333L), new ArrayList<>(shares.values()));
        assertEquals(Arrays.asList("p0", "p1", "p2"), new ArrayList<>(shares.keySet()));
    }

    @Test
    public void equalSupportsLessThanOneCentEachAndIgnoresValues() throws Exception {
        assertEquals(Arrays.asList(1L, 0L, 0L), new ArrayList<>(SplitCalculator.calculate(
                1, SplitType.EQUAL, allocations(-10, 999, 0)).values()));
    }

    @Test
    public void singleParticipantGetsWholeTotalForEveryType() throws Exception {
        for (SplitType type : SplitType.values()) {
            long value = type == SplitType.PERCENTAGE ? 10000 : Long.MAX_VALUE;
            assertEquals(Long.valueOf(Long.MAX_VALUE), SplitCalculator.calculate(
                    Long.MAX_VALUE, type, allocations(value)).get("p0"));
        }
    }

    @Test
    public void percentageUsesLargestRemainderAndNeverRoundsZeroPercentUp() throws Exception {
        assertEquals(Arrays.asList(0L, 0L, 1L), new ArrayList<>(SplitCalculator.calculate(
                1, SplitType.PERCENTAGE, allocations(0, 1000, 9000)).values()));
        assertEquals(Arrays.asList(1L, 0L), new ArrayList<>(SplitCalculator.calculate(
                1, SplitType.PERCENTAGE, allocations(5000, 5000)).values()));
        assertEquals(Arrays.asList(255L, 745L), new ArrayList<>(SplitCalculator.calculate(
                1000, SplitType.PERCENTAGE, allocations(2550, 7450)).values()));
    }

    @Test
    public void exactPreservesSpecifiedAmountsAndInput() throws Exception {
        Map<String, Long> input = allocations(0, 400, 600);
        Map<String, Long> shares = SplitCalculator.calculate(1000, SplitType.EXACT_AMOUNT, input);
        assertEquals(input, shares);
        shares.put("p0", 1L);
        assertEquals(Long.valueOf(0), input.get("p0"));
    }

    @Test
    public void rejectsNonpositiveAmounts() {
        for (long total : new long[]{0, -1, Long.MIN_VALUE}) {
            assertInvalid(total, SplitType.EQUAL, allocations(0),
                    SplitSmartException.Code.INVALID_AMOUNT);
        }
    }

    @Test
    public void rejectsMissingParticipantsTypeAndIds() {
        assertInvalid(100, null, allocations(0), SplitSmartException.Code.INVALID_SPLIT);
        assertInvalid(100, SplitType.EQUAL, null, SplitSmartException.Code.INVALID_SPLIT);
        assertInvalid(100, SplitType.EQUAL, Collections.emptyMap(),
                SplitSmartException.Code.INVALID_SPLIT);
        for (String id : new String[]{null, "", " "}) {
            Map<String, Long> input = new LinkedHashMap<>();
            input.put(id, 0L);
            assertInvalid(100, SplitType.EQUAL, input, SplitSmartException.Code.INVALID_SPLIT);
        }
    }

    @Test
    public void rejectsInvalidPercentagesIncludingOverflowingSum() {
        for (Map<String, Long> input : Arrays.asList(allocations(9999), allocations(10001),
                allocations(-1, 10001), allocations(Long.MAX_VALUE, Long.MAX_VALUE, 10002))) {
            assertInvalid(100, SplitType.PERCENTAGE, input, SplitSmartException.Code.INVALID_SPLIT);
        }
        Map<String, Long> input = allocations(10000);
        input.put("p0", null);
        assertInvalid(100, SplitType.PERCENTAGE, input, SplitSmartException.Code.INVALID_SPLIT);
    }

    @Test
    public void rejectsInvalidExactAmountsIncludingOverflowingSum() {
        for (Map<String, Long> input : Arrays.asList(allocations(99), allocations(101),
                allocations(-1, 101), allocations(Long.MAX_VALUE, Long.MAX_VALUE, 102))) {
            assertInvalid(100, SplitType.EXACT_AMOUNT, input, SplitSmartException.Code.INVALID_SPLIT);
        }
        Map<String, Long> input = allocations(100);
        input.put("p0", null);
        assertInvalid(100, SplitType.EXACT_AMOUNT, input, SplitSmartException.Code.INVALID_SPLIT);
    }

    @Test
    public void randomizedLargePercentageSplitsConserveEveryCent() throws Exception {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 2000; iteration++) {
            long total = iteration == 0 ? Long.MAX_VALUE : (random.nextLong() & Long.MAX_VALUE);
            if (total == 0) total = 1;
            Map<String, Long> input = new LinkedHashMap<>();
            int remaining = 10000;
            for (int i = 0; i < 9; i++) {
                int value = random.nextInt(remaining + 1);
                input.put("p" + i, (long) value);
                remaining -= value;
            }
            input.put("p9", (long) remaining);
            Map<String, Long> shares = SplitCalculator.calculate(total, SplitType.PERCENTAGE, input);
            BigInteger sum = BigInteger.ZERO;
            for (String id : input.keySet()) {
                long cents = shares.get(id);
                assertTrue(cents >= 0);
                BigInteger floor = BigInteger.valueOf(total).multiply(BigInteger.valueOf(input.get(id)))
                        .divide(BigInteger.valueOf(10000));
                BigInteger difference = BigInteger.valueOf(cents).subtract(floor);
                assertTrue(difference.equals(BigInteger.ZERO) || difference.equals(BigInteger.ONE));
                if (input.get(id) == 0) assertEquals(0, cents);
                sum = sum.add(BigInteger.valueOf(cents));
            }
            assertEquals(BigInteger.valueOf(total), sum);
        }
    }

    private static Map<String, Long> allocations(long... values) {
        Map<String, Long> input = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) input.put("p" + i, values[i]);
        return input;
    }

    private static void assertInvalid(long total, SplitType type, Map<String, Long> input,
                                      SplitSmartException.Code code) {
        SplitSmartException failure = assertThrows(SplitSmartException.class,
                () -> SplitCalculator.calculate(total, type, input));
        assertEquals(code, failure.getCode());
    }
}
