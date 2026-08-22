package io.github.jaffe2718.petprofile.ui;

import android.content.Context;
import android.app.DatePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;

public final class ProfileFilterDialog {
    private static final String ALL = "ALL";
    private static final String ACTIVE = "ACTIVE";
    private static final String ARCHIVED = "ARCHIVED";

    private ProfileFilterDialog() {
    }

    public interface Callback {
        void onApply(FilterState state);
    }

    public static class FilterState {
        public Long startDate;
        public Long endDate;
        public String gender = ALL;
        public String status = ALL;
        public String kingdom;
        public String phylum;
        public String taxClass;
        public String taxOrder;
        public String family;
        public String genus;
        public String species;
        public String subspecies;

        public FilterState copy() {
            FilterState state = new FilterState();
            state.startDate = startDate;
            state.endDate = endDate;
            state.gender = gender;
            state.status = status;
            state.kingdom = kingdom;
            state.phylum = phylum;
            state.taxClass = taxClass;
            state.taxOrder = taxOrder;
            state.family = family;
            state.genus = genus;
            state.species = species;
            state.subspecies = subspecies;
            return state;
        }

        public boolean matches(ProfileDetails details) {
            if (details == null || details.profile == null) {
                return false;
            }
            ProfileEntity profile = details.profile;
            long displayTime = details.establishmentTimestamp != null
                    ? details.establishmentTimestamp
                    : profile.createdAt;
            if (startDate != null && displayTime < startDate) {
                return false;
            }
            if (endDate != null && displayTime > endDate) {
                return false;
            }
            if (!ALL.equals(gender) && !gender.equalsIgnoreCase(profile.gender)) {
                return false;
            }
            if (ACTIVE.equals(status) && profile.isArchived()) {
                return false;
            }
            if (ARCHIVED.equals(status) && !profile.isArchived()) {
                return false;
            }
            if (!matchesRank(profile.kingdom, kingdom)
                    || !matchesRank(profile.phylum, phylum)
                    || !matchesRank(profile.taxClass, taxClass)
                    || !matchesRank(profile.taxOrder, taxOrder)
                    || !matchesRank(profile.family, family)
                    || !matchesRank(profile.genus, genus)
                    || !matchesRank(profile.species, species)
                    || !matchesRank(profile.subspecies, subspecies)) {
                return false;
            }
            return true;
        }

        public boolean hasTaxonomyFilter() {
            return !isEmpty(kingdom)
                    || !isEmpty(phylum)
                    || !isEmpty(taxClass)
                    || !isEmpty(taxOrder)
                    || !isEmpty(family)
                    || !isEmpty(genus)
                    || !isEmpty(species)
                    || !isEmpty(subspecies);
        }

        private boolean matchesRank(String value, String selected) {
            if (isEmpty(selected)) {
                return true;
            }
            return selected.equalsIgnoreCase(value == null ? "" : value.trim());
        }

        private boolean isEmpty(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    public static void show(Context context, List<ProfileDetails> allDetails,
                            FilterState current, Callback callback) {
        FilterState working = current == null ? new FilterState() : current.copy();
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_profile_filter, null, false);
        Spinner genderSpinner = view.findViewById(R.id.filterGenderSpinner);
        Spinner statusSpinner = view.findViewById(R.id.filterStatusSpinner);
        Button taxonomyButton = view.findViewById(R.id.filterTaxonomyButton);
        Button startButton = view.findViewById(R.id.filterStartDateButton);
        Button endButton = view.findViewById(R.id.filterEndDateButton);
        ImageButton clearStartButton = view.findViewById(R.id.clearStartDateButton);
        ImageButton clearEndButton = view.findViewById(R.id.clearEndDateButton);

        updateDateButton(context, startButton, working.startDate);
        updateDateButton(context, endButton, working.endDate);
        populateGenderSpinner(context, genderSpinner, working.gender);
        populateStatusSpinner(context, statusSpinner, working.status);
        updateTaxonomyButton(context, taxonomyButton, working);

        startButton.setOnClickListener(v -> pickDate(context, working, true, () -> {
            updateDateButton(context, startButton, working.startDate);
        }));
        endButton.setOnClickListener(v -> pickDate(context, working, false, () -> {
            updateDateButton(context, endButton, working.endDate);
        }));
        clearStartButton.setOnClickListener(v -> {
            working.startDate = null;
            updateDateButton(context, startButton, working.startDate);
        });
        clearEndButton.setOnClickListener(v -> {
            working.endDate = null;
            updateDateButton(context, endButton, working.endDate);
        });
        genderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                working.gender = position == 0 ? ALL : genderValue(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                working.status = position == 0 ? ALL : (position == 1 ? ACTIVE : ARCHIVED);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        taxonomyButton.setOnClickListener(v ->
                showTaxonomyPicker(context, allDetails, working, updated -> {
                    copyTaxonomy(updated, working);
                    updateTaxonomyButton(context, taxonomyButton, working);
                }));

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.action_filter)
                .setView(view)
                .setPositiveButton(R.string.filter_apply, (dialog, which) -> callback.onApply(working.copy()))
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.filter_reset, (dialog, which) -> callback.onApply(new FilterState()))
                .show();
    }

    private static void updateDateButton(Context context, Button button, Long date) {
        MaterialButton materialButton = (MaterialButton) button;
        materialButton.setIconResource(R.drawable.ic_calendar);
        if (date == null) {
            materialButton.setText("");
        } else {
            materialButton.setText(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date(date)));
        }
    }

    private static void pickDate(Context context, FilterState state, boolean start, Runnable after) {
        Calendar calendar = Calendar.getInstance();
        Long value = start ? state.startDate : state.endDate;
        if (value != null) {
            calendar.setTimeInMillis(value);
        }
        new DatePickerDialog(
                context,
                (view, year, month, day) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, day);
                    if (start) {
                        picked.set(Calendar.HOUR_OF_DAY, 0);
                        picked.set(Calendar.MINUTE, 0);
                        picked.set(Calendar.SECOND, 0);
                        picked.set(Calendar.MILLISECOND, 0);
                        state.startDate = picked.getTimeInMillis();
                    } else {
                        picked.set(Calendar.HOUR_OF_DAY, 23);
                        picked.set(Calendar.MINUTE, 59);
                        picked.set(Calendar.SECOND, 59);
                        picked.set(Calendar.MILLISECOND, 999);
                        state.endDate = picked.getTimeInMillis();
                    }
                    after.run();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private static void populateGenderSpinner(Context context, Spinner spinner, String selectedGender) {
        String[] values = {
                context.getString(R.string.label_all),
                context.getString(R.string.gender_unknown),
                context.getString(R.string.gender_female),
                context.getString(R.string.gender_male)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(genderPosition(selectedGender));
    }

    private static void populateStatusSpinner(Context context, Spinner spinner, String selectedStatus) {
        String[] values = {
                context.getString(R.string.label_all),
                context.getString(R.string.filter_status_active),
                context.getString(R.string.filter_status_archived)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(statusPosition(selectedStatus));
    }

    private static int genderPosition(String gender) {
        if ("UNKNOWN".equals(gender)) {
            return 1;
        }
        if ("FEMALE".equals(gender)) {
            return 2;
        }
        if ("MALE".equals(gender)) {
            return 3;
        }
        return 0;
    }

    private static String genderValue(int position) {
        if (position == 1) {
            return "UNKNOWN";
        }
        if (position == 2) {
            return "FEMALE";
        }
        if (position == 3) {
            return "MALE";
        }
        return ALL;
    }

    private static int statusPosition(String status) {
        if (ACTIVE.equals(status)) {
            return 1;
        }
        if (ARCHIVED.equals(status)) {
            return 2;
        }
        return 0;
    }

    private static void updateTaxonomyButton(Context context, Button button, FilterState state) {
        button.setText(taxonomySummary(context, state));
    }

    private static String taxonomySummary(Context context, FilterState state) {
        if (!state.hasTaxonomyFilter()) {
            return context.getString(R.string.filter_taxonomy_choose);
        }
        StringBuilder builder = new StringBuilder();
        appendSummary(builder, context.getString(R.string.label_kingdom), state.kingdom);
        appendSummary(builder, context.getString(R.string.label_phylum), state.phylum);
        appendSummary(builder, context.getString(R.string.label_class), state.taxClass);
        appendSummary(builder, context.getString(R.string.label_order), state.taxOrder);
        appendSummary(builder, context.getString(R.string.label_family), state.family);
        appendSummary(builder, context.getString(R.string.label_genus), state.genus);
        appendSummary(builder, context.getString(R.string.label_species), state.species);
        appendSummary(builder, context.getString(R.string.label_subspecies), state.subspecies);
        return builder.toString();
    }

    private static void appendSummary(StringBuilder builder, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(label).append(" ").append(value.trim());
        }
    }

    private static void showTaxonomyPicker(Context context, List<ProfileDetails> allDetails,
                                           FilterState current, TaxonomyCallback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_taxonomy_filter, null, false);
        ProfileEntity filter = new ProfileEntity();
        filter.kingdom = current.kingdom;
        filter.phylum = current.phylum;
        filter.taxClass = current.taxClass;
        filter.taxOrder = current.taxOrder;
        filter.family = current.family;
        filter.genus = current.genus;
        filter.species = current.species;
        filter.subspecies = current.subspecies;

        final Spinner[] spinners = {
                view.findViewById(R.id.taxKingdomSpinner),
                view.findViewById(R.id.taxPhylumSpinner),
                view.findViewById(R.id.taxClassSpinner),
                view.findViewById(R.id.taxOrderSpinner),
                view.findViewById(R.id.taxFamilySpinner),
                view.findViewById(R.id.taxGenusSpinner),
                view.findViewById(R.id.taxSpeciesSpinner),
                view.findViewById(R.id.taxSubspeciesSpinner)
        };
        final int[] rowIds = {
                R.id.taxKingdomRow,
                R.id.taxPhylumRow,
                R.id.taxClassRow,
                R.id.taxOrderRow,
                R.id.taxFamilyRow,
                R.id.taxGenusRow,
                R.id.taxSpeciesRow,
                R.id.taxSubspeciesRow
        };

        final boolean[] updating = {false};
        for (int index = 0; index < spinners.length; index++) {
            final int rank = index;
            spinners[index].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (updating[0]) {
                        return;
                    }
                    String value = position == 0 ? null : parent.getItemAtPosition(position).toString();
                    setTaxonomyValue(filter, rank, value);
                    for (int lower = rank + 1; lower < spinners.length; lower++) {
                        setTaxonomyValue(filter, lower, null);
                    }
                    populateLowerSpinners(context, allDetails, filter, spinners, rowIds, rank + 1, updating);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        populateLowerSpinners(context, allDetails, filter, spinners, rowIds, 0, updating);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.filter_taxonomy)
                .setView(view)
                .setPositiveButton(R.string.filter_apply, (dialog, which) -> callback.onTaxonomy(filter))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private interface TaxonomyCallback {
        void onTaxonomy(ProfileEntity filter);
    }

    private static void copyTaxonomy(ProfileEntity filter, FilterState state) {
        state.kingdom = filter.kingdom;
        state.phylum = filter.phylum;
        state.taxClass = filter.taxClass;
        state.taxOrder = filter.taxOrder;
        state.family = filter.family;
        state.genus = filter.genus;
        state.species = filter.species;
        state.subspecies = filter.subspecies;
    }

    private static void populateLowerSpinners(Context context, List<ProfileDetails> allDetails,
                                              ProfileEntity filter, Spinner[] spinners, int[] rowIds,
                                              int startRank, boolean[] updating) {
        updating[0] = true;
        try {
            for (int rank = startRank; rank < spinners.length; rank++) {
                List<String> values = taxonomyOptions(allDetails, filter, rank);
                String current = taxonomyValue(filter, rank);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                adapter.add(context.getString(R.string.label_all));
                adapter.addAll(values);

                View row = spinners[rank].getRootView().findViewById(rowIds[rank]);
                if (values.isEmpty()) {
                    row.setVisibility(View.GONE);
                } else {
                    row.setVisibility(View.VISIBLE);
                }

                int selected = 0;
                if (current != null && !current.trim().isEmpty()) {
                    for (int i = 0; i < values.size(); i++) {
                        if (current.equalsIgnoreCase(values.get(i))) {
                            selected = i + 1;
                            break;
                        }
                    }
                }
                spinners[rank].setAdapter(adapter);
                spinners[rank].setSelection(selected);
                if (selected == 0) {
                    setTaxonomyValue(filter, rank, null);
                }
            }
        } finally {
            updating[0] = false;
        }
    }

    private static List<String> taxonomyOptions(List<ProfileDetails> allDetails,
                                                ProfileEntity filter, int rank) {
        Set<String> values = new LinkedHashSet<>();
        for (ProfileDetails details : allDetails) {
            ProfileEntity profile = details.profile;
            if (!matchesHigherRanks(profile, filter, rank)) {
                continue;
            }
            String value = taxonomyValue(profile, rank);
            if (value != null && !value.trim().isEmpty()) {
                values.add(value.trim());
            }
        }
        return new ArrayList<>(values);
    }

    private static boolean matchesHigherRanks(ProfileEntity profile, ProfileEntity filter, int rank) {
        for (int i = 0; i < rank; i++) {
            String selected = taxonomyValue(filter, i);
            if (selected == null || selected.trim().isEmpty()) {
                continue;
            }
            String actual = taxonomyValue(profile, i);
            if (!selected.equalsIgnoreCase(actual == null ? "" : actual.trim())) {
                return false;
            }
        }
        return true;
    }

    private static String taxonomyValue(ProfileEntity profile, int rank) {
        switch (rank) {
            case 0:
                return profile.kingdom;
            case 1:
                return profile.phylum;
            case 2:
                return profile.taxClass;
            case 3:
                return profile.taxOrder;
            case 4:
                return profile.family;
            case 5:
                return profile.genus;
            case 6:
                return profile.species;
            case 7:
                return profile.subspecies;
            default:
                return null;
        }
    }

    private static void setTaxonomyValue(ProfileEntity profile, int rank, String value) {
        switch (rank) {
            case 0:
                profile.kingdom = value;
                break;
            case 1:
                profile.phylum = value;
                break;
            case 2:
                profile.taxClass = value;
                break;
            case 3:
                profile.taxOrder = value;
                break;
            case 4:
                profile.family = value;
                break;
            case 5:
                profile.genus = value;
                break;
            case 6:
                profile.species = value;
                break;
            case 7:
                profile.subspecies = value;
                break;
            default:
                break;
        }
    }
}
