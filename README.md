# SplitSmart

SplitSmart is an Android app for USC students to record shared expenses in a group, split them fairly, and see who owes whom.

Team 02 (DDACK), Project 2.2.

## Running the app

1. Install a recent version of [Android Studio](https://developer.android.com/studio).
2. Clone this repo, then choose **File → Open** in Android Studio and select the cloned folder.
3. Let Gradle sync finish. If Android Studio offers to upgrade the Android Gradle Plugin, it's safe to accept.
4. Run the `app` configuration on an emulator or phone with Android 8.0 (API 26) or newer.

Log in with `tommy@usc.edu` and the password `password`.

Accounts, groups, expenses, expense shares, and settlements are stored in SQLite on the device and survive closing the app. Expense creation, viewing, editing, and deletion use the real handler. Balances and history still use fake in-memory data until Role 5 merges its handlers; they do not yet reflect changes to real expenses. The monthly summary is still unimplemented.

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

The existing `recordSettlement` entry point delegates to Role 3's real settlement repository. Role 5 still needs to include these persistent settlements in balances and history.

## Tests

Run `./gradlew testDebugUnitTest` for split-calculator tests and Robolectric integration tests using SQLite. They cover rounding, large amounts, invalid inputs, current-user and creator permissions, inclusive filters, persistence after reopening, share replacement/deletion, and transaction rollback. Run `./gradlew assembleDebug` to build the app.
