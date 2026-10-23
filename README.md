# Moneta — Personal Finance Android App

The personal finance Android app described in `ARCHITECTURE.md` — see that
file first, it's the product/technical design doc this code follows. This
README tracks what's actually built versus what's deliberately deferred, and
is updated as work continues; it is not a static Phase 1 snapshot.

## What's in this drop (Phases 1–9 complete + Recurring Transactions + Settings + real compiler-verified fixes + icon rendering)

**This is the first revision that has actually been compiled**, via GitHub Actions, and the errors it surfaced (four real Kotlin compile errors, plus a batch of experimental-API warnings) have been fixed — see "Compiler-verified fixes" below. Everything before that point had only been manually reviewed, not built.

- **Project scaffold**: Kotlin, Jetpack Compose, Material 3, Hilt, Room, KSP, Coroutines/Flow.
- **Design system**: brand colors, typography (tabular figures for currency), spacing/radius tokens, a true separate dark palette — see `ui/theme/`.
- **Database (Room, schema v4)**: `Account`, `Category`, `Transaction`, `Tag` (v1), `Budget` (v2), `Goal`/`Bill` (v3), `RecurringTransaction` (v4) — every version bump is a purely additive `Migration`, never a destructive one (Section 45) — see `data/local/`.
- **Repositories**: `AccountRepository`, `CategoryRepository`, `TransactionRepository`. `TransactionRepository` is the single write path for every balance mutation (insert/edit/delete/undo/duplicate), applied atomically via `Room.withTransaction` — see `data/repository/TransactionRepository.kt`.
- **Core utilities**: `Money` (integer minor units, no floats — `core/money/Money.kt`) and `DateRange` (single source of truth for month/week/year boundaries — `core/date/DateRange.kt`).
- **Navigation**: 5-tab bottom nav (Home, Transactions, Budgets, Reports, More) plus a center Quick Add FAB — `ui/navigation/`.
- **Working screens**:
  - **Home** — real balance/income/expense/recent-transactions/savings from the database, a spending-by-category donut chart with a legend (custom-drawn with Compose Canvas, no chart library dependency yet), and a month-over-month spending trend line that only appears once there's a full prior month of data to compare against (Section 19: never invent an observation) — plus a proper empty state that leads straight into adding your first account.
  - **Transactions** — real list with search and an empty state; tapping a row opens Transaction Detail.
  - **Quick Add** — a functioning amount-first entry flow (Expense/Income/Transfer, account/category pickers, notes) that writes through `TransactionRepository`, and can also load an existing transaction to edit it in place.
  - **Transaction Detail** — view a transaction's full details, Edit (reopens Quick Add pre-filled), Duplicate, and Delete with a confirmation dialog and an undo snackbar.
  - **Accounts** (`ui/accounts/`) — list with live balances, add/edit (name, type, color, opening balance locked after creation, include-in-net-worth toggle), and archive-or-delete (archives automatically instead of destroying history when the account has transactions).
  - **Categories** (`ui/categories/`) — Expense/Income tabs, add/edit (name, color), and a safe-delete flow: deleting a category with existing transactions prompts you to reassign them to another category first rather than orphaning your history.
  - **Budgets** (`ui/budgets/`) — a real Room migration (v1→v2, purely additive) adds the `Budget` table. Create an overall or per-category monthly budget with an amount and an alert threshold; the list shows live progress bars colored by status (under/near/over), with spent/remaining amounts computed from the exact same transaction queries the dashboard and (eventually) Reports use, so the numbers can't disagree with each other. Home's "Budget status" snippet shows your top 1–2 budgets by how close/over they are.
  - **Reports** (`ui/reports/`) — five tabs, all built from the same query patterns as the rest of the app so numbers can't disagree: **Overview** (income/expense summary plus a 6-month grouped bar chart), **Spending** and **Income** (full category donut + legend, sharing the exact same `CategoryBreakdownUseCase` Home uses), **Cash flow** (month-by-month income/expense/net), and **Net worth** (a real historical trend reconstructed from opening balances + transaction history — not stored snapshots, and not invented data).
  - **Goals** (`ui/goals/`) — create a savings goal with a target amount (target date is optional and not yet pickable from a calendar UI), see progress, and add money toward it. Contributions update the goal's own saved amount; they deliberately do **not** move money between accounts (the screen says so) — that's a known simplification, not a hidden one.
  - **Bills** (`ui/bills/`) — add a bill with a due-in-days shortcut and optional account/category; the list shows Overdue/Due today/Due soon/Upcoming/Paid computed live from the due date. Marking a bill paid records a real expense transaction through the normal write path *only* when both an account and category were set on the bill — otherwise it just flips the status, and the screen tells you which happened.
  - **Insights** (`domain/insights/`, `ui/insights/`) — a real rule-based engine (`InsightsEngine` + 7 `InsightRule` implementations: category month-over-month change, 3-month consecutive spending increase, average monthly spend, top category this month, budget risk, savings trend, and early-month spending concentration). Every rule is a pure function over a shared `InsightContext` and returns `null` rather than guessing when it doesn't have enough data (Section 19) - the screen shows an honest "not enough data yet" or "nothing notable right now" empty state rather than ever inventing an observation.
  - **Export** (`data/export/`, `ui/importexport/`) — CSV export (proper RFC 4180 quoting/escaping, not naive comma-joining) and a genuine paginated PDF report (Android's built-in `PdfDocument`, no external library) with the app name, period, generated date, an income/expense summary, and a transaction table. Both share one `ExportRowMapper` so they can never disagree on the data. **Deliberately not built**: a real `.xlsx` binary writer - see the note below.
  - **Backup & Restore** (`data/backup/`, `ui/backup/`) — a full local backup (every account, category, transaction, tag, budget, goal, bill, and recurring transaction) to one JSON file the user saves via the system file picker, and a restore flow that requires explicit confirmation before it replaces existing data, running inside a single Room transaction so a failed/corrupted restore can't leave the database half-overwritten. **Cloud backup architecture**: a `CloudBackupProvider` interface (upload/list/download/connect, kept entirely separate from local backup/restore) with one Hilt binding as the single integration point for swapping in a real provider later. The current binding, `UnavailableCloudBackupProvider`, is honestly not connected — see Known Limitations for why.
  - **Recurring Transactions** (`ui/recurring/`) — DAILY/WEEKLY/MONTHLY/YEARLY rules with an interval count (e.g. "every 2 weeks"). `RecurringTransactionRepository.generateDueOccurrences()` walks each active rule forward one occurrence at a time, writing each through `TransactionRepository`'s normal atomic write path (never a shortcut that skips the balance update) and persisting `nextOccurrenceDate` after every single occurrence so a mid-catch-up crash can't duplicate or lose one — with a defensive existence check and a hard cap per pass. Runs automatically each time the app comes to the foreground (see Known Limitations for what that does and doesn't cover). The list screen shows cadence, next date, and an active/pause toggle.
  - **Settings** (`data/settings/`, `ui/settings/`) — DataStore-backed preferences, organized into sections (Appearance, Currency, Modules, Transactions, About). **Fully wired into real behavior**: Theme (System/Light/Dark) actually controls the app's appearance via `MainActivity`; the Budgets/Goals/Bills module toggles actually hide the Budgets tab and the Goals/Bills rows under More, not just a stored flag. **Stored but not yet enforced everywhere** (disclosed directly in the Settings screen's own copy, not hidden): the currency code (still `PKR` by default across most calculations — see Known Limitations), "confirm before deleting" (only the existing Transaction Detail delete dialog honors it; other delete flows don't check it yet), and "compact transaction rows" (no compact layout built yet).
  - **More** — every module now has a real, navigable screen, including Settings.

### Compiler-verified fixes (this revision)

Once this project was actually built for the first time (via the GitHub Actions workflow), the compiler found four real errors that manual review had missed:
- `Icons.AutoMirrored.Outlined.ChevronRight` doesn't exist (that icon was never added to Compose's auto-mirrored set) — switched to `Icons.Outlined.ChevronRight`.
- `MoneyText.kt` imported a nonexistent `FontFeatureSettings` type — removed; `fontFeatureSettings` is just a plain `String` property and the code already used the correct string constant.
- `TransactionDetailScreen.kt` used `.dp` without importing it — added the import (checked every other file for the same missing-import pattern; none found).
- `CategoriesViewModel.kt` called `.distinctUntilChanged()` directly on a `StateFlow`, which kotlinx.coroutines treats as a compile error since `StateFlow` is already conflated — removed (checked the two other `distinctUntilChanged()` call sites in the codebase; both are on plain `Flow`s, not `StateFlow`s, so those were fine).
- Added `@file:OptIn(ExperimentalMaterial3Api::class)` to the 16 files using `SegmentedButton`, the lambda-based `LinearProgressIndicator`, or `ListItem` — all still marked experimental in this Compose BOM version.

### Icon rendering (this revision)

Categories and accounts have always stored an icon name and a color, but almost nothing in the UI actually rendered them — category and account rows showed plain text only. Added `IconMapper` (a deliberately conservative name→icon lookup, after the `ChevronRight` failure showed that assuming an icon exists without checking is a real risk) and a shared `IconBadge` composable (a soft-tinted circular badge per Section 3's design system), now wired into the Accounts and Categories list rows. **Not yet wired everywhere** — Transaction rows (Home's recent list, the Transactions tab) still show text only, since showing a category's icon there needs a category-lookup plumbed into those screens, which wasn't done this pass.

## What's intentionally NOT in this drop yet

CSV/Excel import (Section 23), a real `.xlsx` binary writer (CSV is offered as the fully-working spreadsheet-compatible export in the meantime), custom (non-monthly) budget periods, a global "pause all budgeting" switch, budget push notifications, a goal target-date picker, a periodic background job for recurring-transaction generation (it currently runs on app foreground start — see Known Limitations), and security settings (app lock/PIN/biometric — Section 25 lists this as a potential future addition, not a Phase 10 requirement). Settings itself is now built (see above) but doesn't yet cover every field Section 25 lists — notification permissions and per-notification-type toggles (bill reminders, budget warnings) aren't there, since there's no real notification-sending code yet for them to control. These are scoped to further work, per the brief's "do not casually remove/skip ahead" and "test each phase before moving to the next" requirements.

## Getting a real APK

**See `RELEASE.md`** for the honest full explanation. Short version: this project was authored in a sandbox with no Android SDK and no access to Google's Maven repository, so nothing here has ever actually been compiled - not by choice, but because it's genuinely not possible in that environment. The included `.github/workflows/build.yml` will build a real debug APK automatically the moment you push this to a GitHub repository (GitHub's runners have full SDK/Maven access), which is the fastest path to an installable file without setting anything up locally. The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) is the genuine, unmodified article, fetched directly from `gradle/gradle` at the matching version - not hand-reconstructed.

## Testing

`app/src/test/` has real JVM unit tests (no emulator/device needed - run with `./gradlew test`) for the parts of the codebase where correctness matters most and is most feasible to verify without a full Android environment: `MoneyTest` (the financial-arithmetic value class - Section 32/48 singles this out as needing particular care), `DateRangeTest` (month/period boundary logic - guards the timezone/date-boundary risk called out in `ARCHITECTURE.md`'s risk table), `InsightRulesTest` (confirms each rule returns `null`, not a guess, when it lacks enough data - Section 19's core requirement), and `RecurrenceCalculatorTest` (daily/weekly/monthly/yearly date advancement, including the end-of-month rollover edge case - Jan 31 + 1 month → Feb 28, not an overflow into March). These are a starting point, not full coverage - repository-level tests (ideally with an in-memory Room database and Turbine for Flow assertions, both already available as dependencies) are a natural next addition, especially for `TransactionRepository`'s balance-mutation logic.

## Opening the project

1. Open this folder in **Android Studio** (Koala/2024.1 or newer recommended for AGP 8.5 + Compose Compiler 1.5.14).
2. The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) is already included and genuine (fetched directly from `gradle/gradle` at the matching `8.7` version) — Android Studio should pick it up and sync without needing to regenerate anything.
3. Sync Gradle, then Run on an emulator or device (`minSdk 26`, target/compile `35`).
4. On first launch the app seeds the default category set (Section 12) automatically — there's no seed data for accounts, so you'll land on the Home empty state until you add one via Quick Add's account picker prompt.

## Known limitations (by design, not bugs)

- The amount field is a standard text field with a decimal keyboard, not yet the fully custom numeric keypad described in Section 7 — same data path, simpler input widget for now.
- Category reordering, subcategories, and icon pickers (beyond color) aren't built yet — categories currently use a single default icon plus a user-chosen color.
- The spending-breakdown donut is drawn directly with Compose Canvas rather than a charting library, and shows the top 4 categories in its legend (the donut itself reflects all categories). This keeps Phase 1–3 dependency-free; a dedicated charting approach can be revisited if Phase 5's fuller Reports need more chart types (stacked/area/etc.).
- Dashboard section customization (Section 6: "user should be able to customize which dashboard sections appear") isn't built yet — Settings exists now, but this specific per-widget toggle set isn't among its sections yet.
- Budgets only support the MONTHLY period in this phase (Section 13's custom-period budgets are deferred), and there's no "pause all budgeting" master switch yet — a per-module on/off toggle exists in Settings, but a separate "pause without losing your budgets" switch is a different, still-unbuilt thing.
- Net worth trend has one documented edge case: an account created after a historical date being queried still counts its full opening balance for that date (see the doc comment on `NetWorthTrendUseCase`) - only matters if you ask about net worth from before an account existed.
- Goal contributions are tracked on the goal itself, not linked to a real account transfer — see the in-app copy on the Contribute screen.
- Bills support one-off entries with a quick "due in N days" shortcut, and don't yet link to Recurring Transactions even though both features now exist independently (Section 16's "recurrence" field on a bill) — wiring them together (so a recurring bill automatically appears on the Bills list each cycle) is a reasonable near-term follow-up.
- Recurring transaction generation runs when the app comes to the foreground (`MonetaApplication.onCreate`), which catches up anything missed since it was last opened — but it does **not** run while the app isn't open at all. A periodic WorkManager job is the natural next step for that, once this on-launch version has been exercised for a while.
- Insights recompute on-demand (each time the Insights screen loads) rather than being a live Flow like most of the rest of the app - the underlying data is real and current, but a rule won't visibly update while you're sitting on the screen and add a transaction elsewhere. A pull-to-refresh or auto-refresh-on-return could be added as a small follow-up.
- **No real `.xlsx` writer.** Apache POI (the usual Java library for this) has known compatibility problems on Android, and hand-rolling a raw OOXML zip is real binary-format work I can't verify renders correctly without a device/emulator to actually open the file in Excel. Rather than ship a `.xlsx` that might silently be malformed - which Section 47 explicitly calls out as unacceptable - the Export screen offers CSV (which every spreadsheet app opens natively) and says so plainly. A dedicated `.xlsx` writer, properly tested on-device, is a reasonable follow-up rather than something to fake now.
- Backup is a single JSON file, not a zip - there's no receipt-photo storage built yet (Section 36) for a zip to need to bundle, so JSON alone is a complete, honest backup of everything the app currently stores.
- Restore replaces all data outright (no merge/diff) and requires the explicit in-app confirmation dialog before doing so; there's no separate automatic "safety backup taken just before restore" file yet - Section 24 mentions this as good practice, and it's a reasonable near-term follow-up now that backup/restore itself exists.
- **CSV import has no duplicate detection.** Download the template under More > Import, fill it in a spreadsheet, and pick the file back up - it validates every row (unmatched accounts, missing required fields, bad dates/amounts) and shows a preview of what will and won't import before anything is written, auto-creating any category named in the sheet that doesn't exist yet. What it does *not* do is notice if a row (or the whole file) has already been imported before - importing the same file twice creates duplicate transactions rather than being caught as a re-run. Section 23's duplicate-detection pass (matching on date+amount+account, or an import batch/undo concept) is a reasonable near-term follow-up.
- **Cloud backup is architected but not connected.** A real Google Drive integration needs Google Sign-In/Credential Manager OAuth, the Drive REST API, and a `google-services.json` tied to a real Google Cloud project - none of which can be created inside this codebase; it has to come from whoever publishes the app. Rather than fake a "Connect Google Drive" button that silently does nothing (Section 47 explicitly forbids this), the Backup screen shows an honest "not connected" state with a real explanation, and the `CloudBackupProvider` interface plus its single Hilt binding (`CloudBackupModule`) is the complete, ready-to-use seam for plugging in a real implementation later - swapping the binding is the only change anything else in the app would need. Multi-device **sync** (as distinct from backup) remains explicitly out of scope, per the architecture doc's own caution about conflict-resolution complexity.
- The currency code set in Settings is stored and editable, but most amount calculations across the app still default to a hardcoded `PKR` constant rather than reading it - fully threading a user-selected currency through every ViewModel that currently hardcodes `DEFAULT_CURRENCY = "PKR"` is a real, sizeable refactor (dozens of call sites) that I chose to disclose rather than rush partially. The Settings screen says this plainly rather than implying the currency picker fully works everywhere yet.
- This project **has now actually been compiled and built successfully** (via GitHub Actions), which is a meaningfully stronger guarantee than the manual review that covered everything before it. That said, a successful compile only proves the code is syntactically and type-correct — it doesn't prove every screen behaves exactly as intended at runtime. If something looks or behaves oddly on your device, that's a real bug report, not something already ruled out.

## v0.13.0 - fast Quick Add, date/time, scheduling, welcome setup

- Quick Add rebuilt for speed: amount field opens with the keyboard, category/account chips with icons, pinned Save bar, "Save & add another".
- Date and time on every entry: Today / Yesterday / date picker, and a time picker. Past dates are recorded as back-dated transactions.
- Future dates become one-time scheduled items (shown under More -> Recurring). They turn into real transactions, and change balances, the next time the app starts on or after that date. The time of day is not kept for scheduled items.
- First-run welcome flow (name, accounts, expense categories, then record a transaction or import). "Do this later" leaves a reminder on Home.
- CI: Gradle caching; the release APK is built only on manual "Run workflow" runs.

## v0.14.0 - smart import

- Accepts Excel (.xlsx) and CSV (comma, semicolon or tab separated). Old .xls must be re-saved as .xlsx or CSV.
- Only Date and Amount are required. Column names are matched loosely (e.g. "Description" = Notes).
- Auto-repaired: many date formats (2026-01-15, 15/01/2026, 15 Jan 2026, Excel date numbers), times (9:30 PM, 21:30, Excel times), amounts (Rs. 1,250.50, (450), -450), type words (debit/credit...), missing Type guessed from the amount's sign.
- Accounts and categories named in the file but not in Moneta are created automatically (new accounts start at balance 0). A blank category goes to "Uncategorized"; a blank account uses your first account.
- Nothing is saved until you review the summary (new accounts/categories, auto-fixes, skipped rows, totals, sample rows) and confirm.
- Rows are skipped only if the date or amount is unusable, or a transfer has no/same destination account.
- Duplicates: rows matching an existing transaction (same date, amount, type and account) are skipped.

## v0.15.0 - overhaul

- Quick Add: type, amount, account/category (chips or dropdown, switchable), one tappable date and time, notes.
- Budgets: every expense category listed automatically; tap to set, edit or delete a budget; totals for budget/spent/remaining.
- Home: per-account balances, month income/expense with breakdown (savings removed).
- Records: period navigation (daily/weekly/monthly/yearly/all/custom), totals, account filter, accounts shown per row.
- Reports: period selection, pie charts, cash-flow trends, accounts/net worth tab.
- Accounts: opening balance editable; optional bottom-bar tab (Settings > Advanced settings > Customization).
- Side drawer replaces the More tab; whole-app search; in-app notifications (bell).
- Export: any custom period. Settings: change your name, carry-forward and budget rollover options.

## v0.16.0 - refinements

- Home is now a clean dashboard: collapsible total balance, tappable Income / Expenses / Net change tiles.
- Notifications only badge new alerts; they count as seen once you open and leave the list.
- Budgets are per month: a new month starts empty with "Copy from <previous month>" or "Set this month's budgets"; a three-dot menu edits or deletes; tapping a budget shows its transactions.
- Accounts: tap for a month-by-month history, archived accounts look different.
- Categories have an icon picker.
- Add transaction: full-screen pop-up pickers with search, date/time below notes, no gap above the keyboard, a toast confirms saves and failures.
- Settings > Advanced > Customization: show/hide and reorder the bottom-bar tabs.
- Record rows show category > subcategory, account and notes; transfers are blue.
- Cleaner whole-app search, and a new app icon.

## v0.17.0 - settings, reports, import

- Add transaction is customizable (Settings > Customization > Record form): save buttons on top, account and category in one row, pickers or notes above the amount, built-in calculator number pad.
- Records: account tag and description look different, times only in the details pop-up (tap a record), compact one-row search/filter, new overview tile (Income, Expenses, Bal.), daily totals styled apart.
- Plus buttons can be dragged and snap back; transaction and other (amber) plus buttons differ in colour. Home shows the last 5 records.
- Budgets: new "Auto-set from <last month> spending" option. Insights removed.
- Currency setting works (labels only) with a show/hide symbol switch.
- Reports: Overview statement (opening, income, expenses, closing, budgeted-not-spent, expected closing) with tappable figures; category analysis pop-up in Spending; Trends tab and bar chart removed.
- Category form is compact; each icon has its own colour.
- Side-menu pages share one header with a back button. Notifications can be marked read/unread, deleted, or opened.
- Import: Excel template, column matching for any file, duplicate detection, compact review. Export: Excel/PDF/CSV with your name and period in the file name.
- Backup: "Download backup", automatic backups on this phone (daily/weekly, taken when the app is opened), restore from them.
- Settings redesigned into areas. Daily reminder notification (inexact alarm; needs notification permission).

## v0.17.1 - speed and size

- Amount formatting is cached (it was rebuilding a number formatter for every amount on screen).
- Release build: baseline profile for faster start, English-only library strings, trimmed packaging. CI now builds the release APK on every push; install that one (the debug APK is unshrunk and much slower).

## v0.18.0
- Quick Add: fixed (non-scrolling) layout, bigger amount/notes/keypad, Android keyboard overlays the built-in keypad, modern account/category pop-ups, optional date/time below the keypad.
- Settings: removed duplicates of side-menu items; About moved to the side menu.
- Onboarding: "Restore from a backup file". Splash screen with app icon.
- Balance scope (All / Current / Savings) on Home; Accounts grouped into Current and Savings.

## v0.19.0
- Home: hide/show balance eye, three-dots All/Current/Savings menu, tap-to-open breakdown (grouped Current vs Savings), four tiles (Opening, Income, Expenses, Current balance) with tap-through breakdowns.
- Budgets: expected savings (actual savings for closed months) and percentage of each budget used.
- Accounts: default 4th tab, dashboard of All/Current/Savings; removed from Reports.
- Reports > Spending: tapping a category opens a full analysis page.
- Records: group by day / week / category, or sort by amount.

## v0.20.0
- Backup now includes all settings (name, theme, tab order, form options, dashboard cards, decimals, etc.).
- Home: bigger bold balance and tiles, budget cards (budgeted / spent / yet to spend), expected closing balance, show/hide each card in Settings, balance hide persists, 0/1/2 decimals option, current period highlighted.
- Records: filter, sort and search icons; category-totals view.
- Budgets: outlook dashboard (total, spent, remaining, yet to spend, expected over/under). Long-press a category to open its report.
- Reports: three equal tabs (Overview, Expenses, Income), inline category analysis with period bar kept, per-period history, stats, transactions list.
- Optional sub-categories (Settings > Modules), nestable, each with own budget and analysis.
- Quick Add: keyboard only opens on tapping a field, bigger amount, cancel button, "+ New" account/category pop-ups, multi-level category pickers.
- Header bar scrolls away while scrolling.

## v0.20.1
- Build fix: experimental-API opt-in for the scrolling header.

## v0.21.0
- Transaction form: save row at top, type, account + category in one line, description, amount, date/time, keypad. Description is capped (no longer stretches); amount/keypad shrink when a sub-category row appears.
- Home: removed "Spent (budgeted)" card; all cards can be reordered/hidden in Settings; Yet to spend opens a compact breakdown.
- Records: separate "view as" button (records / category totals); period label opens a calendar, a small button picks day/week/month/year/all/custom.
- Budgets: no "Expected spend" tile; "Expected Deviation" (excess in red); parent categories show the total of their sub-categories and expand to show them.
- Reports: aligned overview, six-period income vs expenses chart, expenses/income tabs with totals and six-period bars, category analysis bar chart (budget, actual, % of budget, % of total), parent/sub-category tree; no "+" button.
- Back button: opening Budgets/Reports/Records from another screen now steps forward so Back returns; Reports closes an open category first.

## v0.22.0
- Home: optional compact mode (month total with opening/income/expenses, one budget card, expected closing).
- Records: one row of equally sized controls (filter, sort, view as, period type, search); slimmer period bar.
- Budgets: Yet to spend and Expected deviation side by side (Unused / Final deviation for past months); a category can be set to no budget with 0 or blank.
- Categories: compact form (icon + name on one line, icon list in a pop-up, parent as a drop-down).
- Accounts: bold figures and tiles consistent with the rest of the app.
- Reports: six-period chart now sits below the category list on Expenses and Income.
- Settings: one organised page (Profile, General, Money and budgets, Home page, Layout, Reminders); name saves reliably.
- Backup: optional extra copy of automatic backups in a folder you choose; cloud placeholder card removed.

## v0.22.1
- Build fix: missing icon import in the category form.

## v1.0.0 - Moneta Plus, ads, Play Store release
- One-time **Moneta Plus** purchase (Play Billing, product id `moneta_plus`), 14-day free trial, restore purchase.
- Free tier: rewarded ad unlocks charts and PDF report for 24 hours. Plus: no ads, sub-categories, compact Home and card order, scheduled backups and folder copy.
- Entitlement kept in its own DataStore (not in backups, excluded from Android auto-backup).
- CI builds a signed Play Store bundle; see PLAY_STORE_SETUP.md and the `store/` folder.
