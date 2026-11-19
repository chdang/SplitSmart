# SplitSmart - Tests 16-20

**Project 2.4 testing contribution | Team 02 (DDACK)**  
**Contributor:** Kyuhong Lee - kyuhongl@usc.edu  
**Assigned area:** Settlements & Expense Splitting (R9-R11, R16)  
**Validated:** November 18, 2026  
**Base:** Initial test baseline a2b4213; integrated with main at ca0c3d5 before publication.

## Preface

This contribution contains Kyuhong's five black-box cases and five white-box cases, numbered 16-20 in each section to match the team plan. It supplies the test descriptions, rationales, outcomes, coverage, and defect notes for insertion into the team's final testing PDF. The team's existing title/authors, preface, and other members' cases should remain in the combined document. Tests 11-15 for expense management remain Daniel's section.

## Instructions

Open the project in Android Studio with Gradle JDK 21 and let it sync. Local tests use JUnit 4 and Robolectric with real SQLite at API 28. Espresso tests need an emulator or device with API 26 or later. The actual UI run used the available Pixel 4 emulator, Android API 37, in a disposable read-only session. These tests reset this app's local database and session before every case, so use a disposable emulator. They use the seeded Tommy and Alex accounts; login password is `password`.

Run the five white-box cases and generate their isolated JaCoCo report:

```bash
./gradlew :app:testDebugUnitTest --tests '*SettlementsAndSplittingWhiteBoxTest' :app:createDebugUnitTestCoverageReport
```

Run the five black-box cases on a running emulator:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=edu.usc.splitsmart.ui.expense.SettlementsAndSplittingBlackBoxTest
```

For one local case, use `--tests 'edu.usc.splitsmart.logic.SettlementsAndSplittingWhiteBoxTest.METHOD'`. For one UI case, replace the class argument with `'edu.usc.splitsmart.ui.expense.SettlementsAndSplittingBlackBoxTest#METHOD'`. Substitute the method printed below, or right-click that method in Android Studio and Run. Each invocation performs its own fresh setup.

Local results: `app/build/reports/tests/testDebugUnitTest/index.html`. UI results: `app/build/reports/androidTests/connected/debug/index.html`. Coverage: `app/build/reports/coverage/test/debug/index.html` and `report.xml` in that directory. Copies of the run's XML evidence and coverage counters are in `docs/testing/evidence/`.

## Black-Box Testing (Espresso)
### Black-box Test 16: Equal split keeps every penny

**Author:** Kyuhong Lee. **Requirements:** R9.

**Location:** `app/src/androidTest/java/edu/usc/splitsmart/ui/expense/SettlementsAndSplittingBlackBoxTest.java`, test `test16EqualSplitConservesPenniesAfterReopening()`.

**Description:** Tommy logs in, opens Apartment 4B, and creates a $10.00 equal expense shared by Tommy, Alex, and Sam. The test checks the displayed participant order: the first selected row owes $3.34 and the next two owe $3.33. After saving and reopening the expense, it checks the same previews, $10.00 total, and Equally selection.

**Rationale / coverage intent:** Boundary-value analysis uses a total that is not divisible by three. The extra cent must be assigned once, not lost or duplicated. Reopening checks that the saved split is reproduced by the form.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** No product defect reproduced.

### Black-box Test 17: Percentage total validation and one-cent rounding

**Author:** Kyuhong Lee. **Requirements:** R10.

**Location:** `app/src/androidTest/java/edu/usc/splitsmart/ui/expense/SettlementsAndSplittingBlackBoxTest.java`, test `test17PercentageRequiresOneHundredAndPreservesRounding()`.

**Description:** Tommy enters a $0.01 expense for Tommy and Alex, selects By percent, and enters 33.33% for Tommy. Alex values of 66.66% and 66.68% must each show the 100% validation message and keep the form open. With 66.67%, previews must be $0.00 and $0.01. The saved expense must reopen with By percent selected and the original percentages intact.

**Rationale / coverage intent:** Boundary values of 99.99%, 100.00%, and 100.01% distinguish under-allocation, valid allocation, and over-allocation. A one-cent total exposes rounding and loss of the original percentages much more clearly than a whole-dollar example.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** Confirmed the team-draft defect: the edit form highlighted Equally while showing stored percentage allocations. The saved mode was selected while Material buttons were disabled and ignored the state change. Fixed selectSplitType to enable options only during its synchronous update and then restore their prior permission states. BB17 now checks the correct selected mode and original values.

### Black-box Test 18: Exact shares reject a one-cent mismatch

**Author:** Kyuhong Lee. **Requirements:** R11.

**Location:** `app/src/androidTest/java/edu/usc/splitsmart/ui/expense/SettlementsAndSplittingBlackBoxTest.java`, test `test18ExactAmountsMustMatchTheTotal()`.

**Description:** Tommy creates a $10.00 expense for Tommy and Alex using By amount. Tommy is assigned $4.00. Alex values of $5.99 and $6.01 must each show that shares need to total $10.00. With Alex at $6.00, saving succeeds. Reopening must show By amount and exact shares of $4.00 and $6.00.

**Rationale / coverage intent:** A pair of deliberately unequal shares distinguishes exact splitting from accidental equal splitting. Totals of $9.99, $10.00, and $10.01 exercise both sides of the exact-total boundary.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** Found the same disabled-button defect for exact splits: By amount was not highlighted on reopen. Fixed by the selectSplitType update described for BB17; this case verifies the exact-mode regression.

### Black-box Test 19: Partial and final settlements persist

**Author:** Kyuhong Lee. **Requirements:** R16.

**Location:** `app/src/androidTest/java/edu/usc/splitsmart/ui/expense/SettlementsAndSplittingBlackBoxTest.java`, test `test19PartialAndFinalSettlementsAreSavedAndReduceDebt()`.

**Description:** Through the expense form, Tommy records that Alex paid $30.00 split equally between them, creating a $15.00 debt. In Record a payment, Tommy records $5.00 with the note Cash at lunch. The group must now show $10.00 payable. The test opens the $5.00 payment from history and checks its amount, payer, receiver, and saved note. A second payment uses the suggested remaining $10.00; both payable and receivable totals must then be $0.00.

**Rationale / coverage intent:** Equivalence partitions cover a partial payment and a payment exactly equal to the remaining debt. Checking payment details after navigating away verifies persistence and direction; the final zero boundary checks that the debt is cancelled exactly.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** No product defect reproduced.

### Black-box Test 20: Invalid settlements leave the debt unchanged

**Author:** Kyuhong Lee. **Requirements:** R16.

**Location:** `app/src/androidTest/java/edu/usc/splitsmart/ui/expense/SettlementsAndSplittingBlackBoxTest.java`, test `test20InvalidOrExcessSettlementsDoNotChangeDebt()`.

**Description:** Starting with the same $15.00 debt, Tommy submits a payment without choosing a member and with $0.00. The form must show errors on the recipient and amount fields. Selecting Alex still must not permit zero. A $15.01 payment must show a maximum of $15.00. Choosing They paid me and $1.00 must show a maximum of $0.00 because Alex owes Tommy nothing. Returning to the group must still show $15.00 payable and $0.00 receivable.

**Rationale / coverage intent:** Negative equivalence classes cover a missing recipient, a nonpositive amount, an overpayment by one cent, and a payment in the wrong direction. The unchanged group totals verify that rejected requests have no financial effect.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** No product defect reproduced.

## White-Box Testing (JUnit / Robolectric)
### White-box Test 16: Equal-split validation and penny branches

**Author:** Kyuhong Lee. **Requirements:** R9.

**Location:** `app/src/test/java/edu/usc/splitsmart/logic/SettlementsAndSplittingWhiteBoxTest.java`, test `equalSplitCoversValidationAndPennyRemainderBranches()`.

**Description:** Calls SplitCalculator.calculate through the amount, missing-type/map, empty-map, and invalid-ID guards. Then checks $10.00/3 as 334/333/333 cents, one cent/3 as 1/0/0, two cents/2 as 1/1, and a single participant receiving Long.MAX_VALUE cents.

**Rationale / coverage intent:** Exercise the guarded failures, the equal-return path, both outcomes of the extra-penny condition, and one/multiple iterations. Include zero shares and the largest representable positive total.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** No product defect reproduced.

### White-box Test 17: Percentage validation, remainder order, and overflow

**Author:** Kyuhong Lee. **Requirements:** R10.

**Location:** `app/src/test/java/edu/usc/splitsmart/logic/SettlementsAndSplittingWhiteBoxTest.java`, test `percentageSplitCoversValidationOrderingAndOverflowSafeMath()`.

**Description:** Checks under/over-100% weights, negative weights, and null weights. Valid cases include 25.5%/74.5%, largest-remainder ordering that must skip a 0% participant, and a 50%/50% tie that must favor the first participant. At Long.MAX_VALUE cents with 3333/6667 basis points, independent BigInteger arithmetic verifies conservation and that rounding is at most one positive cent above the mathematical floor.

**Rationale / coverage intent:** Exercise percentage validation success/failure, zero and nonzero rounding loops, stable tie ordering, and multiplication near the long limit. The oracle uses independent arbitrary-precision arithmetic.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** No product defect reproduced.

### White-box Test 18: Exact-allocation bounded validation

**Author:** Kyuhong Lee. **Requirements:** R11.

**Location:** `app/src/test/java/edu/usc/splitsmart/logic/SettlementsAndSplittingWhiteBoxTest.java`, test `exactSplitCoversBoundedValidationAndCopiesAllocations()`.

**Description:** Checks a valid 0/400/600-cent split of 1000 cents and verifies that changing the returned map does not mutate the caller's allocations. Rejects totals one cent under/over, a negative amount, a null amount, and an overflow-shaped allocation list containing Long.MAX_VALUE twice.

**Rationale / coverage intent:** Exercise each value guard and both zero/nonzero outcomes of the bounded remainder check. The large values ensure overflowing addition cannot turn an invalid split into an accepted one.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** No product defect reproduced.

### White-box Test 19: Settlement guards stop invalid writes

**Author:** Kyuhong Lee. **Requirements:** R16.

**Location:** `app/src/test/java/edu/usc/splitsmart/logic/SettlementsAndSplittingWhiteBoxTest.java`, test `settlementValidationRejectsInvalidRequestsWithoutWriting()`.

**Description:** Creates a real SQLite debt fixture, then calls recordSettlement with zero/negative amounts; blank, null, or identical people; an unknown user; a known user outside the group; a current recorder outside the group; and no logged-in user. It checks the corresponding INVALID_AMOUNT, INVALID_INPUT, NOT_A_MEMBER, or NOT_LOGGED_IN error and confirms no settlement row was written.

**Rationale / coverage intent:** Exercise the compound settlement guards, participant and recorder membership checks, missing-session path, and asynchronous error delivery. All callbacks must occur once on the main thread.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** No product defect reproduced.

### White-box Test 20: Debt limit and editing exclude the existing payment

**Author:** Kyuhong Lee. **Requirements:** R16.

**Location:** `app/src/test/java/edu/usc/splitsmart/logic/SettlementsAndSplittingWhiteBoxTest.java`, test `settlementDebtBoundaryAndEditExcludeExistingPayment()`.

**Description:** With a $15.00 debt, rejects $15.01 and a payment in the opposite direction. Saves $5.00, then edits that same payment to $15.00: this must succeed because requireWithinDebt must exclude the old $5.00 row while validating the edit. A $15.01 edit must fail and retain $15.00. Restores $5.00, adds the exact $10.00 remainder, rejects a further cent, deletes the second payment, checks NOT_FOUND for it, and rejects another member's deletion and creator impersonation.

**Rationale / coverage intent:** Exercise both outcomes of the ignoreId branch in the settlement loop, empty/nonempty settlement lists, equal/above/zero debt limits, and recorder checks. This detects the subtle bug where an edited payment is incorrectly counted against itself.

**Execution:** Run this method in Android Studio or use the single-case command described above.

**Result:** Passed.

**Bugs:** No product defect reproduced.

## White-box coverage

JaCoCo executable-line coverage and branch coverage were measured using only the five methods in `SettlementsAndSplittingWhiteBoxTest`. Line coverage is covered executable source lines divided by all executable source lines. Branch coverage is covered instrumented decision branches divided by total branches; it is not MC/DC, all-path, or mutation coverage.

| Production class | Line coverage | Branch coverage |
| --- | --- | --- |
| SplitCalculator | 43/43 = 100.0% | 38/38 = 100.0% |
| DefaultExpenseHandler | 93/147 = 63.3% | 45/74 = 60.8% |

Targeted settlement helper coverage from WB19-WB20:

| Method in DefaultExpenseHandler | Line coverage | Branch coverage |
| --- | --- | --- |
| checkSettlement | 7/7 = 100.0% | 8/8 = 100.0% |
| requireWithinDebt | 12/12 = 100.0% | 6/6 = 100.0% |
| requireRecorder | 3/3 = 100.0% | 4/4 = 100.0% |

The first table covers each entire named class; the second table is scoped to the listed helper methods. `DefaultExpenseHandler` also contains expense CRUD, retrieval, and other entry points beyond this assigned section, so its percentage is not a settlement-only or whole-app claim. `SplitCalculator` achieves 100% line and branch coverage from WB16-WB18. Within the handler, `checkSettlement` and `requireWithinDebt` achieve 100% of their executable lines and branches; exact counters are preserved in the coverage evidence. Recorder checks also exercise correct recorder, different recorder, and spoofed actor. Coverage does not imply all possible input combinations or paths have been tested.

## Defects, fixes, and limitations

- All ten assigned cases passed after fixing the edit-form split selector in `ExpenseFormActivity.selectSplitType`. BB17/BB18 reproduced the problem: Material buttons ignored programmatic checked-state changes while the creator check had disabled the form. The fix temporarily enables the three options for the synchronous selection update and then restores each option's original enabled state. It does not grant editing rights to a non-creator.
- The first UI run passed BB16, BB19, and BB20, while BB17/BB18 failed because a transient validation banner could cover Save during the next tap. The test harness now waits for that banner to disappear before continuing. This was a synchronization correction to the tests, not a product-code fix.
- The team draft had already reported the percentage-highlight issue. These tests confirmed its cause and also reproduced it for exact-amount edits. Both regression cases now pass. The bug was visual mode selection; the calculator and persisted allocations were correct.
- The initial baseline ran 56 local cases and had six failures in `DefaultGroupHandlerOwnerCheckTest` because its fixture did not sign in. The team subsequently fixed that fixture on main. After integrating the latest main, the complete local suite passed all 73 tests. Our application authentication rules were unchanged.
- The isolated white-box result and coverage are from the five assigned cases. A separate post-fix regression run also passed all 29 tests in ExpenseIntegrationTest, SplitCalculatorTest, and SettlementsAndSplittingWhiteBoxTest, with lint reporting no errors. The later full local run, after integration with main, passed all 73 tests. Only this five-case Espresso class was run on the emulator. No all-device or all-Android-version claim is made.

## Team handoff

Paste the five black-box entries into the team's Black-Box Testing section as cases 16-20, the five white-box entries into White-Box Testing as cases 16-20, and include this coverage subsection and its exact reproduction command. Keep Kyuhong Lee listed as owner of these ten cases. Preserve the full team roster and other members' sections in the final combined PDF. The final team submission still needs the entire project ZIP and one combined testing PDF.
