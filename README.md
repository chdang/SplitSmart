# SplitSmart

SplitSmart is an Android app for USC students to record shared expenses in a group, split them fairly, and see who owes whom.

Team 02 (DDACK), Project 2.2.

## Running the app

1. Install a recent version of [Android Studio](https://developer.android.com/studio).
2. Clone this repo, then choose **File → Open** in Android Studio and select the cloned folder.
3. Let Gradle sync finish. If Android Studio offers to upgrade the Android Gradle Plugin, it's safe to accept.
4. Run the `app` configuration on an emulator or phone with Android 8.0 (API 26) or newer.

Log in with `tommy@usc.edu` and the password `password`.

Accounts, groups, expenses, expense shares, and settlements are stored in SQLite on the device and survive closing the app. Expense creation, viewing, editing, and deletion use the real handler. Balances, history, and the monthly summary (Role 5) also use real data: they are recalculated from the stored expenses and settlements on every view, so edits, deletions, and new settlements show up right away.

The database is seeded with five sample accounts (`tommy@usc.edu`, `alex@usc.edu`, `sam@usc.edu`, `jordan@usc.edu`, and `priya@usc.edu`), all with the password `password`, plus two sample groups with no expenses. New accounts can create groups and expenses. Uninstalling the app clears local data; the next launch recreates the sample accounts and groups.

## Project structure

The code follows the layers in our design document. Each layer only talks to the one below it.

| Folder (`app/src/main/java/edu/usc/splitsmart/`) | Layer | What goes here |
| --- | --- | --- |
| `ui/` | Presentation | Activities, layouts, and adapters. No business rules. |
| `logic/` | Business logic | Handler interfaces and their implementations: sessions, groups, expenses, balances. |
| `repository/` | Database management | `UserRepository` and `GroupRepository`, the only code that talks to the database. |
| `model/` | Data | Plain data classes: `User`, `Group`, `Expense`, `Settlement`, and so on. |

Screens never call repositories directly. They call handlers through `AppServices`, which decides whether each handler is the real one or a fake.

## Team workflow

- `main` is protected. Nobody pushes to it directly.
- Work on a branch named `feature/<short-name>`, such as `feature/split-calculator`.
- Open a pull request into `main` and get one teammate's approval before merging.
- Pull `main` into your branch at least once a day to avoid big merge conflicts.
- Commit each working piece separately, with a message that says what it does.
- Each requirement (R1–R19) has a GitHub issue. Write `Closes #<issue>` in the pull request that finishes it.
- Changes to shared files (models, handler interfaces, `AppServices`) go in their own small pull request that the team agrees on.

## Money

All amounts are stored as whole cents in a `long`, never as `double` or `float`. $12.50 is `1250`. Percentages are stored in basis points: 25.5% is `2550`, and 100% is `10000`.

## Expenses (Role 4, R6–R11)

`DefaultExpenseHandler` uses the current session, validates group membership for the creator, payer, and every participant, and saves through `GroupRepository`. The creator can log a payment made by another member. Only the creator can edit or delete it; changing the payer does not transfer edit rights. Editing preserves the expense ID, creator, creation time, and group, and atomically replaces its shares. Deleting removes its shares too. Database work runs off the main thread, and callbacks return on the main thread.

`SplitCalculator.calculate(totalCents, splitType, allocations)` works without Android or a database. Use a `LinkedHashMap` to specify participant order. Equal splits give leftover pennies to the first participants. Percentage splits require exactly 10000 basis points and give rounding pennies to the largest fractional remainders, breaking ties by participant order. Exact amounts must sum to the total. Zero shares are allowed, but totals must be positive. All three modes support totals up to `Long.MAX_VALUE` without overflowing.

Percentage shares also retain the original basis points so reopening the edit form does not reconstruct percentages from rounded cents. Database version 2 adds this optional field without deleting existing data. Older shares without stored percentages use the form's reconstruction fallback. Share previews use the same calculator as saving.

Role 5 can call `ExpenseHandler.getExpenses(groupId, userId, fromDate, toDate, callback)` to combine user and date filters. Each filter can be `null` to disable it. A user matches as payer or participant (including a zero share); being the creator alone does not match. Both date endpoints are inclusive and apply to the expense date, not the creation time. A reversed date range returns `INVALID_INPUT`. Results contain shares and sort by expense date descending, then creation time descending, then ID ascending for ties. The original unfiltered `getExpenses` method remains available.

The existing `recordSettlement` entry point delegates to Role 3's real settlement repository. Role 5's balances and history read these persistent settlements from the same repository.

## Receipts

The expense form has a Receipts section. While adding an expense, the creator can attach photos from the system photo picker. Those photos are saved together with the expense. While editing, attaching or deleting a receipt is saved immediately. Tapping a thumbnail opens the full image.

**Scan receipt** opens Google's document scanner from Google Play services. The scanner finds the receipt's edges, flattens and crops the image, and can capture up to five pages. Each page becomes its own receipt, and a multi-page scan is saved all together or not at all. On devices without Google Play services, the button opens the camera app instead. Neither path needs a camera permission. The camera hands its photo back through a `FileProvider` limited to one cache folder. Every member of the group can view receipts. Only the expense's creator can add or delete them, which is the same rule that applies to editing the expense.

Photos are shrunk to 1600 px on the long side and stored as JPEG `BLOB`s (≤ 1 MB each) in the `receipts` table, which database version 4 adds. That table's `expense_id` foreign key cascades, so deleting an expense or its group also deletes its receipts. Permission checks are in `DefaultReceiptHandler`, and SQL is in `SqliteReceiptRepository`. `ReceiptIntegrationTest` (Robolectric, real SQLite) and `ReceiptManagementEspressoTest` (on-device, with the photo picker stubbed through Espresso-Intents) cover this. `ReceiptScanningEspressoTest` covers scanning through the camera fallback, because Espresso can't drive the document scanner, which runs in Play services.

## Reminders

On the **Who owes whom** screen, each member who owes you money has a **Remind** button. After you confirm, they get an in-app payment reminder for what they owe you right now, after expenses and settlements are netted. Members who owe you nothing don't get the button. If they pay you back before you press it, you see a message and the balances refresh.

Reminders you've received are in the bell icon on the groups screen. A banner there shows how many are unread. Each reminder shows who sent it, the amount, the group, and the date it was sent. Unread reminders are bold and marked **New**. You can mark each one as read or unread. Tapping a reminder marks it read and opens that group's balances so you can settle up.

Reminders are stored in the `reminders` table, which database version 5 adds. A reminder keeps the amount from when it was sent, and deleting its group deletes it. `DefaultReminderHandler` checks who may remind whom, reusing the balance netting from `DefaultUserGroupBalanceHandler`. Only the recipient can change a reminder's read state. `ReminderIntegrationTest` (Robolectric, real SQLite) and `PaymentReminderEspressoTest` (on-device) cover this.

## Tests

Run `./gradlew testDebugUnitTest` for split-calculator tests and Robolectric integration tests using SQLite. They cover rounding, large amounts, invalid inputs, current-user and creator permissions, inclusive filters, persistence after reopening, share replacement/deletion, and transaction rollback. Run `./gradlew assembleDebug` to build the app.

### Project 2.4: Kyuhong's tests 16-20

Kyuhong Lee's contribution covers settlements and expense splitting (R9-R11, R16): five Espresso black-box tests and five JUnit/Robolectric white-box tests. The numbered cases, rationale, results, coverage, and team-report handoff are in [the testing contribution](docs/testing/kyuhong-tests-16-20.md). The formatted report is [available as a PDF](output/pdf/splitsmart-kyuhong-tests-16-20.pdf).

Use Gradle JDK 21. Run only the five white-box cases and generate their JaCoCo coverage:

```bash
./gradlew :app:testDebugUnitTest --tests '*SettlementsAndSplittingWhiteBoxTest' :app:createDebugUnitTestCoverageReport
```

Start a disposable emulator, then run only the five black-box cases:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=edu.usc.splitsmart.ui.expense.SettlementsAndSplittingBlackBoxTest
```

The UI tests reset the app's local database and session before each case. Local test results are in `app/build/reports/tests/testDebugUnitTest/index.html`, UI results in `app/build/reports/androidTests/connected/debug/index.html`, and coverage in `app/build/reports/coverage/test/debug/index.html`. The initial baseline identified six group-owner fixture failures, which the team subsequently fixed on main. After integrating the latest main, the complete local suite passed all 73 tests; the app and instrumented-test APKs also built successfully. The five assigned Espresso cases were run separately on an emulator.
