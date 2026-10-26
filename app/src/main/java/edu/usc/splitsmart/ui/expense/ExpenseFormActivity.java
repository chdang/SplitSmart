package edu.usc.splitsmart.ui.expense;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.ExpenseDraft;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Dates;
import edu.usc.splitsmart.ui.common.DropdownAdapter;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;
import edu.usc.splitsmart.ui.common.Labels;
import edu.usc.splitsmart.ui.common.Money;
import edu.usc.splitsmart.ui.common.SimpleTextWatcher;

/**
 * The expense form shared by NewExpenseForm and EditExpenseForm: description, amount, payer,
 * date, category, and how to split it (R6, R9-R11). It checks the split adds up before sending
 * it, and shows a live "left to assign" line while the user types.
 */
public abstract class ExpenseFormActivity extends GroupScopedActivity {

    private static final String STATE_FORM = "expense_form";

    private enum Status { NEUTRAL, GOOD, PROBLEM }

    protected final List<User> members = new ArrayList<>();
    private final List<ParticipantRow> rows = new ArrayList<>();

    private TextInputLayout descriptionLayout;
    private TextInputEditText descriptionInput;
    private TextInputLayout amountLayout;
    private TextInputEditText amountInput;
    private TextInputLayout payerLayout;
    private AutoCompleteTextView payerInput;
    private TextInputLayout dateLayout;
    private TextInputEditText dateInput;
    private TextInputLayout categoryLayout;
    private AutoCompleteTextView categoryInput;
    private MaterialButtonToggleGroup splitTypeGroup;
    private LinearLayout participantsContainer;
    private TextView splitStatus;
    private ColorStateList neutralStatusColor;
    protected TextView readOnlyBanner;
    protected Button saveButton;
    protected Button deleteButton;

    private User payer;
    private Category category = Category.FOOD;
    private LocalDate expenseDate = LocalDate.now();
    private SplitType splitType = SplitType.EQUAL;
    private boolean editable = true;
    private Bundle restoredForm;

    /** The toolbar title. */
    @StringRes
    protected abstract int titleRes();

    /**
     * Called once members are loaded and the participant rows exist. If {@code restored} is
     * true, the form was refilled from before a rotation and shouldn't be reset.
     */
    protected abstract void onFormReady(boolean restored);

    /** Sends the checked draft to the handler. */
    protected abstract void submit(ExpenseDraft draft);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_form);
        setUpToolbar(getString(titleRes()), true);
        if (savedInstanceState != null) {
            restoredForm = savedInstanceState.getBundle(STATE_FORM);
        }

        readOnlyBanner = findViewById(R.id.read_only_banner);
        descriptionLayout = findViewById(R.id.description_layout);
        descriptionInput = findViewById(R.id.description_input);
        amountLayout = findViewById(R.id.amount_layout);
        amountInput = findViewById(R.id.amount_input);
        payerLayout = findViewById(R.id.payer_layout);
        payerInput = findViewById(R.id.payer_input);
        dateLayout = findViewById(R.id.date_layout);
        dateInput = findViewById(R.id.date_input);
        categoryLayout = findViewById(R.id.category_layout);
        categoryInput = findViewById(R.id.category_input);
        splitTypeGroup = findViewById(R.id.split_type_group);
        participantsContainer = findViewById(R.id.participants_container);
        splitStatus = findViewById(R.id.split_status);
        neutralStatusColor = splitStatus.getTextColors();
        saveButton = findViewById(R.id.save_button);
        deleteButton = findViewById(R.id.delete_button);

        // These show values this class tracks itself, so it restores them after rotation.
        payerInput.setSaveEnabled(false);
        categoryInput.setSaveEnabled(false);
        dateInput.setSaveEnabled(false);
        for (int i = 0; i < splitTypeGroup.getChildCount(); i++) {
            splitTypeGroup.getChildAt(i).setSaveEnabled(false);
        }

        setUpCategoryPicker();
        setUpDatePicker();
        payerInput.setOnItemClickListener((parent, view, position, id) -> {
            payer = members.get(position);
            payerLayout.setError(null);
        });
        splitTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                applySplitType(splitTypeFor(checkedId));
            }
        });
        amountInput.addTextChangedListener(SimpleTextWatcher.after(this::updateSplitStatus));
        saveButton.setOnClickListener(v -> save());
        saveButton.setEnabled(false);

        startGroupScreen();
    }

    @Override
    protected void onMemberVerified() {
        AppServices.groups().getMembers(groupId, onUi(list -> {
            members.clear();
            members.addAll(list);
            setUpPayerPicker();
            buildParticipantRows();
            saveButton.setEnabled(true);
            boolean restored = restoredForm != null && restoredForm.getBoolean("rows");
            if (restored) {
                restoreForm(restoredForm);
            }
            onFormReady(restored);
        }, this::closeWithError));
    }

    // ---------------------------------------------------------------------------------------
    // Filling in the form (used by subclasses)
    // ---------------------------------------------------------------------------------------

    protected void selectPayer(User user) {
        payer = user;
        payerInput.setText(Labels.nameMarkingYou(this, user, currentUser.getId()), false);
    }

    protected void checkEveryone() {
        for (ParticipantRow row : rows) {
            row.setChecked(true);
        }
        updateSplitStatus();
    }

    /** Fills every field from an existing expense. */
    protected void fill(Expense expense) {
        descriptionInput.setText(expense.getDescription());
        amountInput.setText(Money.toInput(expense.getTotalCents()));
        setCategory(expense.getCategory());
        setExpenseDate(expense.getExpenseDate());
        for (User member : members) {
            if (member.getId().equals(expense.getPayerId())) {
                selectPayer(member);
            }
        }
        selectSplitType(expense.getSplitType());

        Map<String, Long> percents = percentagesOf(expense);
        for (ParticipantRow row : rows) {
            String id = row.user.getId();
            row.setChecked(expense.hasShareFor(id));
            if (!expense.hasShareFor(id)) {
                continue;
            }
            if (expense.getSplitType() == SplitType.EXACT_AMOUNT) {
                row.setValueText(Money.toInput(expense.owedBy(id)));
            } else if (expense.getSplitType() == SplitType.PERCENTAGE) {
                row.setValueText(Money.percentToInput(percents.get(id)));
            }
        }
        updateSplitStatus();
    }

    /** Turns the form into a read-only view for people who didn't create the expense. */
    protected void setEditable(boolean canEdit) {
        editable = canEdit;
        descriptionLayout.setEnabled(canEdit);
        amountLayout.setEnabled(canEdit);
        payerLayout.setEnabled(canEdit);
        dateLayout.setEnabled(canEdit);
        categoryLayout.setEnabled(canEdit);
        for (int i = 0; i < splitTypeGroup.getChildCount(); i++) {
            splitTypeGroup.getChildAt(i).setEnabled(canEdit);
        }
        for (ParticipantRow row : rows) {
            row.setEditable(canEdit);
        }
        saveButton.setVisibility(canEdit ? View.VISIBLE : View.GONE);
    }

    /** Shows the loading bar and stops double taps while a save or delete is in progress. */
    protected void setSaving(boolean saving) {
        setLoading(saving);
        saveButton.setEnabled(!saving);
        deleteButton.setEnabled(!saving);
    }

    // ---------------------------------------------------------------------------------------
    // Pickers
    // ---------------------------------------------------------------------------------------

    private void setUpPayerPicker() {
        List<String> names = new ArrayList<>();
        for (User member : members) {
            names.add(Labels.nameMarkingYou(this, member, currentUser.getId()));
        }
        payerInput.setAdapter(new DropdownAdapter(this, names));
    }

    private void setUpCategoryPicker() {
        List<String> labels = new ArrayList<>();
        for (Category option : Category.values()) {
            labels.add(Labels.category(this, option));
        }
        categoryInput.setAdapter(new DropdownAdapter(this, labels));
        categoryInput.setOnItemClickListener((parent, view, position, id) ->
                category = Category.values()[position]);
        setCategory(category);
    }

    private void setCategory(Category newCategory) {
        category = newCategory;
        categoryInput.setText(Labels.category(this, newCategory), false);
    }

    private void setUpDatePicker() {
        setExpenseDate(expenseDate);
        dateInput.setOnClickListener(v -> {
            if (!editable) {
                return;
            }
            DatePickerDialog dialog = new DatePickerDialog(this,
                    (picker, year, monthIndex, day) ->
                            setExpenseDate(LocalDate.of(year, monthIndex + 1, day)),
                    expenseDate.getYear(), expenseDate.getMonthValue() - 1,
                    expenseDate.getDayOfMonth());
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            dialog.show();
        });
    }

    private void setExpenseDate(LocalDate date) {
        expenseDate = date;
        dateInput.setText(Dates.fullDate(date));
    }

    // ---------------------------------------------------------------------------------------
    // Split type and participants
    // ---------------------------------------------------------------------------------------

    private void buildParticipantRows() {
        participantsContainer.removeAllViews();
        rows.clear();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (User member : members) {
            ParticipantRow row = new ParticipantRow(inflater, participantsContainer, member,
                    Labels.nameMarkingYou(this, member, currentUser.getId()),
                    this::updateSplitStatus);
            row.setSplitType(splitType);
            rows.add(row);
            participantsContainer.addView(row.view);
        }
    }

    private void selectSplitType(SplitType type) {
        splitTypeGroup.check(buttonFor(type));
        // check() only notifies the listener when the selection changes.
        if (type != splitType) {
            applySplitType(type);
        }
    }

    private void applySplitType(SplitType type) {
        splitType = type;
        for (ParticipantRow row : rows) {
            row.setSplitType(type);
        }
        updateSplitStatus();
    }

    private static SplitType splitTypeFor(int buttonId) {
        if (buttonId == R.id.split_percent) {
            return SplitType.PERCENTAGE;
        } else if (buttonId == R.id.split_exact) {
            return SplitType.EXACT_AMOUNT;
        }
        return SplitType.EQUAL;
    }

    private static int buttonFor(SplitType type) {
        switch (type) {
            case PERCENTAGE:
                return R.id.split_percent;
            case EXACT_AMOUNT:
                return R.id.split_exact;
            case EQUAL:
            default:
                return R.id.split_equal;
        }
    }

    private List<ParticipantRow> checkedRows() {
        List<ParticipantRow> checked = new ArrayList<>();
        for (ParticipantRow row : rows) {
            if (row.isChecked()) {
                checked.add(row);
            }
        }
        return checked;
    }

    /** Updates each person's share preview and the line saying whether the split adds up. */
    private void updateSplitStatus() {
        if (rows.isEmpty()) {
            return;
        }
        for (ParticipantRow row : rows) {
            row.setPreview(null);
        }
        Long total = Money.parseCents(amountInput.getText());
        List<ParticipantRow> checked = checkedRows();
        if (checked.isEmpty()) {
            showStatus(getString(R.string.split_status_pick_people), Status.PROBLEM);
            return;
        }

        switch (splitType) {
            case EQUAL: {
                if (total != null && total > 0) {
                    // Leftover cents go to the first people listed, as SplitType.EQUAL describes.
                    long base = total / checked.size();
                    long extra = total % checked.size();
                    for (int i = 0; i < checked.size(); i++) {
                        checked.get(i).setPreview(Money.format(base + (i < extra ? 1 : 0)));
                    }
                }
                showStatus(getResources().getQuantityString(R.plurals.split_status_equal,
                        checked.size(), checked.size()), Status.NEUTRAL);
                break;
            }
            case PERCENTAGE: {
                long sum = 0;
                for (ParticipantRow row : checked) {
                    Long basisPoints = row.value();
                    if (basisPoints != null) {
                        sum += basisPoints;
                        if (total != null) {
                            row.setPreview(Money.format(total * basisPoints / 10000));
                        }
                    }
                }
                long left = 10000 - sum;
                if (left == 0) {
                    showStatus(getString(R.string.split_status_percent_done), Status.GOOD);
                } else if (left > 0) {
                    showStatus(getString(R.string.split_status_percent_left,
                            Money.formatPercent(left)), Status.PROBLEM);
                } else {
                    showStatus(getString(R.string.split_status_percent_over,
                            Money.formatPercent(-left)), Status.PROBLEM);
                }
                break;
            }
            case EXACT_AMOUNT: {
                if (total == null) {
                    showStatus(getString(R.string.split_status_enter_total), Status.NEUTRAL);
                    break;
                }
                long sum = 0;
                for (ParticipantRow row : checked) {
                    Long cents = row.value();
                    if (cents != null) {
                        sum += cents;
                    }
                }
                long left = total - sum;
                if (left == 0) {
                    showStatus(getString(R.string.split_status_amount_done, Money.format(total)),
                            Status.GOOD);
                } else if (left > 0) {
                    showStatus(getString(R.string.split_status_amount_left, Money.format(left)),
                            Status.PROBLEM);
                } else {
                    showStatus(getString(R.string.split_status_amount_over, Money.format(-left)),
                            Status.PROBLEM);
                }
                break;
            }
        }
    }

    private void showStatus(String text, Status status) {
        splitStatus.setText(text);
        if (status == Status.GOOD) {
            splitStatus.setTextColor(ContextCompat.getColor(this, R.color.money_owed_to_you));
        } else if (status == Status.PROBLEM) {
            splitStatus.setTextColor(ContextCompat.getColor(this, R.color.money_you_owe));
        } else {
            splitStatus.setTextColor(neutralStatusColor);
        }
    }

    /**
     * The percentage each participant paid, in basis points, rebuilt from their owed amounts.
     * The rounding remainder goes to the largest share so the total is exactly 100%.
     */
    private static Map<String, Long> percentagesOf(Expense expense) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (expense.getTotalCents() <= 0) {
            return result;
        }
        long sum = 0;
        String largest = null;
        for (int i = 0; i < expense.getShares().size(); i++) {
            String id = expense.getShares().get(i).getUserId();
            long owed = expense.getShares().get(i).getOwedCents();
            long basisPoints = Math.round(owed * 10000.0 / expense.getTotalCents());
            result.put(id, basisPoints);
            sum += basisPoints;
            if (largest == null || basisPoints > result.get(largest)) {
                largest = id;
            }
        }
        if (largest != null) {
            result.put(largest, result.get(largest) + (10000 - sum));
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------------------------

    private void save() {
        if (!editable) {
            return;
        }
        ExpenseDraft draft = buildDraft();
        if (draft != null) {
            setSaving(true);
            submit(draft);
        }
    }

    /** Checks the form and builds the draft, or shows what's wrong and returns null. */
    @Nullable
    private ExpenseDraft buildDraft() {
        descriptionLayout.setError(null);
        amountLayout.setError(null);
        payerLayout.setError(null);
        boolean valid = true;

        String description = textOf(descriptionInput);
        if (description.isEmpty()) {
            descriptionLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        Long total = Money.parseCents(amountInput.getText());
        if (total == null || total <= 0) {
            amountLayout.setError(getString(R.string.error_amount_invalid));
            valid = false;
        }
        if (payer == null) {
            payerLayout.setError(getString(R.string.error_pick_payer));
            valid = false;
        }

        List<ParticipantRow> checked = checkedRows();
        Map<String, Long> allocations = new LinkedHashMap<>();
        long sum = 0;
        for (ParticipantRow row : checked) {
            row.showError(null);
            long value = 0;
            if (splitType != SplitType.EQUAL) {
                Long parsed = row.value();
                if (parsed == null) {
                    row.showError(getString(R.string.error_participant_value));
                    valid = false;
                    continue;
                }
                value = parsed;
            }
            allocations.put(row.user.getId(), value);
            sum += value;
        }

        if (checked.isEmpty()) {
            showMessage(getString(R.string.split_status_pick_people));
            return null;
        }
        if (!valid) {
            return null;
        }
        if (splitType == SplitType.PERCENTAGE && sum != 10000) {
            showMessage(getString(R.string.error_split_percent_total));
            return null;
        }
        if (splitType == SplitType.EXACT_AMOUNT && sum != total) {
            showMessage(getString(R.string.error_split_exact_total, Money.format(total)));
            return null;
        }
        return new ExpenseDraft(groupId, payer.getId(), description, total, category, splitType,
                expenseDate, allocations);
    }

    // ---------------------------------------------------------------------------------------
    // Keeping the form through a rotation
    // ---------------------------------------------------------------------------------------

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle form = new Bundle();
        form.putString("category", category.name());
        form.putString("date", expenseDate.toString());
        form.putString("split", splitType.name());
        if (payer != null) {
            form.putString("payer", payer.getId());
        }
        form.putBoolean("rows", !rows.isEmpty());
        ArrayList<String> checkedIds = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        for (ParticipantRow row : rows) {
            if (row.isChecked()) {
                checkedIds.add(row.user.getId());
                values.add(row.valueText());
            }
        }
        form.putStringArrayList("checked", checkedIds);
        form.putStringArrayList("values", values);
        outState.putBundle(STATE_FORM, form);
    }

    private void restoreForm(Bundle form) {
        setCategory(Category.valueOf(form.getString("category", Category.FOOD.name())));
        setExpenseDate(LocalDate.parse(form.getString("date", LocalDate.now().toString())));
        String payerId = form.getString("payer");
        for (User member : members) {
            if (member.getId().equals(payerId)) {
                selectPayer(member);
            }
        }
        selectSplitType(SplitType.valueOf(form.getString("split", SplitType.EQUAL.name())));
        List<String> checkedIds = form.getStringArrayList("checked");
        List<String> values = form.getStringArrayList("values");
        for (ParticipantRow row : rows) {
            int index = checkedIds == null ? -1 : checkedIds.indexOf(row.user.getId());
            row.setChecked(index >= 0);
            if (index >= 0 && values != null) {
                row.setValueText(values.get(index));
            }
        }
        updateSplitStatus();
    }
}
