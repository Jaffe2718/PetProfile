package io.github.jaffe2718.petprofile.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.RecordType;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;

public final class RecordFilterDialog {
    private static final String ALL = "ALL";

    private RecordFilterDialog() {
    }

    public interface Callback {
        void onApply(FilterState state);
    }

    public static class FilterState {
        public Long startDate;
        public Long endDate;
        public String type = ALL;
        public LinkedHashSet<String> selectedFields = new LinkedHashSet<>();

        public FilterState copy() {
            FilterState state = new FilterState();
            state.startDate = startDate;
            state.endDate = endDate;
            state.type = type;
            state.selectedFields = new LinkedHashSet<>(selectedFields);
            return state;
        }

        public boolean matches(RecordEntity record,
                               Map<String, List<RecordFieldEntity>> fieldsByRecord) {
            if (record == null) {
                return false;
            }
            if (startDate != null && record.timestamp < startDate) {
                return false;
            }
            if (endDate != null && record.timestamp > endDate) {
                return false;
            }
            if (!ALL.equals(type) && !type.equals(record.type)) {
                return false;
            }
            if (!selectedFields.isEmpty()) {
                Set<String> present = new LinkedHashSet<>();
                List<RecordFieldEntity> fields = fieldsByRecord == null
                        ? null
                        : fieldsByRecord.get(record.id);
                if (fields != null) {
                    for (RecordFieldEntity field : fields) {
                        if (field.fieldName != null && !field.fieldName.trim().isEmpty()) {
                            present.add(field.fieldName.trim());
                        } else if (field.fieldKey != null && !field.fieldKey.trim().isEmpty()) {
                            present.add(field.fieldKey.trim());
                        }
                    }
                }
                for (String selected : selectedFields) {
                    if (!present.contains(selected)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    public static void show(Context context, List<String> allFieldNames,
                            FilterState current, Callback callback) {
        FilterState working = current == null ? new FilterState() : current.copy();
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_record_filter, null, false);
        Button startButton = view.findViewById(R.id.recordFilterStartDateButton);
        Button endButton = view.findViewById(R.id.recordFilterEndDateButton);
        ImageButton clearStartButton = view.findViewById(R.id.clearRecordStartDateButton);
        ImageButton clearEndButton = view.findViewById(R.id.clearRecordEndDateButton);
        Spinner typeSpinner = view.findViewById(R.id.recordFilterTypeSpinner);
        LinearLayout selectedContainer = view.findViewById(R.id.recordSelectedFieldsContainer);
        LinearLayout candidateContainer = view.findViewById(R.id.recordSelectableFieldsContainer);

        List<String> orderedFieldNames = new ArrayList<>();
        if (allFieldNames != null) {
            for (String name : allFieldNames) {
                if (name != null && !name.trim().isEmpty()
                        && !orderedFieldNames.contains(name.trim())) {
                    orderedFieldNames.add(name.trim());
                }
            }
        }

        updateDateButton(context, startButton, working.startDate);
        updateDateButton(context, endButton, working.endDate);
        bindTypeSpinner(context, typeSpinner, working.type);
        renderFields(context, selectedContainer, candidateContainer, working.selectedFields,
                orderedFieldNames);

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
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                working.type = typeFromPosition(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.action_filter)
                .setView(view)
                .setPositiveButton(R.string.filter_apply, (dialog, which) ->
                        callback.onApply(working.copy()))
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.filter_reset, (dialog, which) ->
                        callback.onApply(new FilterState()))
                .show();
    }

    private static void bindTypeSpinner(Context context, Spinner spinner, String type) {
        String[] values = {
                context.getString(R.string.label_all),
                context.getString(R.string.record_establishment),
                context.getString(R.string.record_daily),
                context.getString(R.string.record_transfer),
                context.getString(R.string.record_archive)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(typePosition(type));
    }

    private static int typePosition(String type) {
        switch (type) {
            case RecordType.ESTABLISHMENT:
                return 1;
            case RecordType.DAILY:
                return 2;
            case RecordType.TRANSFER:
                return 3;
            case RecordType.ARCHIVE:
                return 4;
            default:
                return 0;
        }
    }

    private static String typeFromPosition(int position) {
        switch (position) {
            case 1:
                return RecordType.ESTABLISHMENT;
            case 2:
                return RecordType.DAILY;
            case 3:
                return RecordType.TRANSFER;
            case 4:
                return RecordType.ARCHIVE;
            default:
                return ALL;
        }
    }

    private static void renderFields(Context context, LinearLayout selectedContainer,
                                     LinearLayout candidateContainer,
                                     LinkedHashSet<String> selected,
                                     List<String> orderedFieldNames) {
        selectedContainer.removeAllViews();
        candidateContainer.removeAllViews();
        for (String name : orderedFieldNames) {
            if (selected.contains(name)) {
                selectedContainer.addView(createTag(context, name, true,
                        v -> {
                            selected.remove(name);
                            renderFields(context, selectedContainer, candidateContainer,
                                    selected, orderedFieldNames);
                        }));
            } else {
                candidateContainer.addView(createTag(context, name, false,
                        v -> {
                            selected.add(name);
                            renderFields(context, selectedContainer, candidateContainer,
                                    selected, orderedFieldNames);
                        }));
            }
        }
    }

    private static View createTag(Context context, String text, boolean selected,
                                  View.OnClickListener onClick) {
        TextView tag = new TextView(context);
        tag.setText(text);
        tag.setTextSize(12);
        tag.setGravity(Gravity.CENTER);
        tag.setSingleLine(true);
        tag.setTypeface(Typeface.DEFAULT);
        tag.setTextColor(ContextCompat.getColor(context,
                selected ? R.color.button_soft_text : R.color.text_primary));
        tag.setBackgroundResource(selected ? R.drawable.bg_tag_selected : R.drawable.bg_tag_candidate);
        int padH = dp(context, 14);
        int padV = dp(context, 8);
        tag.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(context, 32));
        lp.setMarginEnd(dp(context, 8));
        tag.setLayoutParams(lp);
        tag.setOnClickListener(onClick);
        return tag;
    }

    private static void updateDateButton(Context context, Button button, Long date) {
        MaterialButton materialButton = (MaterialButton) button;
        materialButton.setIconResource(R.drawable.ic_calendar);
        if (date == null) {
            materialButton.setText("");
        } else {
            materialButton.setText(new java.text.SimpleDateFormat("yyyy-MM-dd",
                    java.util.Locale.getDefault()).format(new java.util.Date(date)));
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

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
