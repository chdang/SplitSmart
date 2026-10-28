# SplitSmart

SplitSmart is an Android app for USC students to record shared expenses in a group, split them fairly, and see who owes whom.

Team 02 (DDACK), Project 2.2.

## Running the app

1. Install a recent version of [Android Studio](https://developer.android.com/studio).
2. Clone this repo, then choose **File → Open** in Android Studio and select the cloned folder.
3. Let Gradle sync finish. If Android Studio offers to upgrade the Android Gradle Plugin, it's safe to accept.
4. Run the `app` configuration on an emulator or phone with Android 8.0 (API 26) or newer.

Log in with `tommy@usc.edu` and the password `password`.

Accounts are real: sign-up, login, logout, and the profile name are stored in a SQLite database on the device and survive closing the app. Groups, expenses, and balances still come from fake in-memory data and are lost when the app closes, until roles 3 to 5 merge their handlers.

The database is seeded with five sample accounts (`tommy@`, `alex@`, `sam@`, `jordan@`, and `priya@usc.edu`), all with the password `password`. An account you create through sign-up works, but its group screens are empty until the real group handlers land. To start from an empty database, uninstall the app from the device.

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
