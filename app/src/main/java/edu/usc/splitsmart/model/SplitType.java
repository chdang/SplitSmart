package edu.usc.splitsmart.model;

/** How an expense is divided among its participants (R9-R11). */
public enum SplitType {
    /** Everyone pays the same amount. Leftover cents go to the first participants listed. */
    EQUAL,
    /** Each participant pays a percentage of the total. The percentages add up to 100. */
    PERCENTAGE,
    /** Each participant pays an exact amount. The amounts add up to the total. */
    EXACT_AMOUNT
}
