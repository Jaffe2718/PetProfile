package io.github.jaffe2718.petprofile.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;
import io.github.jaffe2718.petprofile.util.IdUtil;

public class RoutineAdapter extends RecyclerView.Adapter<RoutineAdapter.Holder> {
    private static final String[] DAY_LABELS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    public interface Listener {
        void onDelete(RoutineEntity routine);
    }

    private final List<RoutineEntity> items = new ArrayList<>();
    private final Listener listener;

    public RoutineAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<RoutineEntity> routines) {
        items.clear();
        if (routines != null) {
            items.addAll(routines);
        }
        notifyDataSetChanged();
    }

    public List<RoutineEntity> getItems() {
        return new ArrayList<>(items);
    }

    public RoutineEntity addRoutine() {
        RoutineEntity routine = new RoutineEntity();
        routine.id = IdUtil.randomId();
        routine.type = RoutineEntity.TYPE_WEEKLY;
        routine.enabled = true;
        routine.position = items.size();
        java.util.Calendar now = java.util.Calendar.getInstance();
        routine.hour = now.get(java.util.Calendar.HOUR_OF_DAY);
        routine.minute = now.get(java.util.Calendar.MINUTE);
        routine.second = now.get(java.util.Calendar.SECOND);
        routine.weekdays = "0,1,2,3,4,5,6";
        items.add(routine);
        notifyItemInserted(items.size() - 1);
        return routine;
    }

    public void remove(RoutineEntity routine) {
        int index = items.indexOf(routine);
        if (index < 0) {
            return;
        }
        items.remove(index);
        notifyItemRemoved(index);
        for (int i = index; i < items.size(); i++) {
            items.get(i).position = i;
        }
        notifyItemRangeChanged(index, items.size() - index);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_routine, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final EditText titleEdit;
        private final EditText detailsEdit;
        private final SwitchCompat enableSwitch;
        private final ImageButton deleteButton;
        private final RadioGroup typeGroup;
        private final RadioButton onceRadio;
        private final RadioButton weeklyRadio;
        private final LinearLayout weeklyLayout;
        private final LinearLayout onceLayout;
        private final LinearLayout dayContainer;
        private final Button timeButton;
        private final Button onceTimeButton;
        private RoutineEntity routine;

        Holder(@NonNull View itemView) {
            super(itemView);
            titleEdit = itemView.findViewById(R.id.routineTitleEditText);
            detailsEdit = itemView.findViewById(R.id.routineDetailsEditText);
            enableSwitch = itemView.findViewById(R.id.routineSwitch);
            deleteButton = itemView.findViewById(R.id.routineDeleteButton);
            typeGroup = itemView.findViewById(R.id.routineTypeRadioGroup);
            onceRadio = itemView.findViewById(R.id.routineOnceRadio);
            weeklyRadio = itemView.findViewById(R.id.routineWeeklyRadio);
            weeklyLayout = itemView.findViewById(R.id.routineWeeklyLayout);
            onceLayout = itemView.findViewById(R.id.routineOnceLayout);
            dayContainer = itemView.findViewById(R.id.routineDayContainer);
            timeButton = itemView.findViewById(R.id.routineTimeButton);
            onceTimeButton = itemView.findViewById(R.id.routineOnceTimeButton);

            bindListeners();
        }

        private void bindListeners() {
            typeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                boolean weekly = checkedId == R.id.routineWeeklyRadio;
                if (routine != null) {
                    routine.type = weekly ? RoutineEntity.TYPE_WEEKLY : RoutineEntity.TYPE_ONCE;
                }
                weeklyLayout.setVisibility(weekly ? View.VISIBLE : View.GONE);
                onceLayout.setVisibility(weekly ? View.GONE : View.VISIBLE);
            });
            timeButton.setOnClickListener(v -> pickWeeklyTime());
            onceTimeButton.setOnClickListener(v -> pickOnceTime());
            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDelete(routine);
                }
            });
            enableSwitch.setOnCheckedChangeListener((b, checked) -> {
                if (routine != null) {
                    routine.enabled = checked;
                }
            });
            titleEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (routine != null) {
                        routine.title = s.toString();
                    }
                }
            });
            detailsEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (routine != null) {
                        routine.details = s.toString();
                    }
                }
            });
        }

        void bind(RoutineEntity entity) {
            routine = entity;
            titleEdit.setText(entity.title);
            detailsEdit.setText(entity.details);
            enableSwitch.setChecked(entity.enabled);
            if (RoutineEntity.TYPE_ONCE.equals(entity.type)) {
                onceRadio.setChecked(true);
            } else {
                weeklyRadio.setChecked(true);
            }
            setupDayButtons();
            renderDayButtons();
            updateTimeButton();
            updateOnceTimeButton();
            boolean weekly = RoutineEntity.TYPE_WEEKLY.equals(entity.type);
            weeklyLayout.setVisibility(weekly ? View.VISIBLE : View.GONE);
            onceLayout.setVisibility(weekly ? View.GONE : View.VISIBLE);
        }

        private void setupDayButtons() {
            if (dayContainer.getChildCount() > 0) {
                return;
            }
            int size = dp(itemView, 38);
            for (int i = 0; i < 7; i++) {
                TextView day = new TextView(itemView.getContext());
                day.setText(DAY_LABELS[i]);
                day.setTextSize(10f);
                day.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                if (i > 0) {
                    lp.setMarginStart(dp(itemView, 6));
                }
                day.setLayoutParams(lp);
                final int index = i;
                day.setOnClickListener(v -> toggleDay(index));
                dayContainer.addView(day);
                day.setTag(index);
            }
        }

        private void renderDayButtons() {
            java.util.Set<Integer> selected = parseWeekdays(routine.weekdays);
            for (int i = 0; i < dayContainer.getChildCount(); i++) {
                View day = dayContainer.getChildAt(i);
                boolean isSelected = selected.contains(i);
                day.setBackgroundResource(isSelected ? R.drawable.bg_day_selected : R.drawable.bg_day_unselected);
                ((TextView) day).setTextColor(ContextCompat.getColor(itemView.getContext(),
                        isSelected ? android.R.color.white : R.color.button_soft_text));
            }
        }

        private void toggleDay(int index) {
            java.util.Set<Integer> selected = parseWeekdays(routine.weekdays);
            if (selected.contains(index)) {
                selected.remove(index);
            } else {
                selected.add(index);
            }
            routine.weekdays = joinWeekdays(selected);
            renderDayButtons();
        }

        private void updateTimeButton() {
            timeButton.setText(String.format(Locale.US, "%02d:%02d:%02d",
                    routine.hour, routine.minute, routine.second));
        }

        private void updateOnceTimeButton() {
            if (routine.onceAt != null && routine.onceAt > 0) {
                onceTimeButton.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()).format(new Date(routine.onceAt)));
            } else {
                onceTimeButton.setText(itemView.getContext().getString(R.string.filter_choose_date));
            }
        }

        private void pickWeeklyTime() {
            int h = routine.hour;
            int m = routine.minute;
            int s = routine.second;
            if (h == 0 && m == 0 && s == 0) {
                java.util.Calendar now = java.util.Calendar.getInstance();
                h = now.get(java.util.Calendar.HOUR_OF_DAY);
                m = now.get(java.util.Calendar.MINUTE);
                s = now.get(java.util.Calendar.SECOND);
            }
            showTimePicker(itemView.getContext(), h, m, s,
                    (newH, newM, newS) -> {
                        routine.hour = newH;
                        routine.minute = newM;
                        routine.second = newS;
                        updateTimeButton();
                    });
        }

        private void pickOnceTime() {
            new android.app.DatePickerDialog(
                    itemView.getContext(),
                    (view, year, month, day) -> {
                        java.util.Calendar now = java.util.Calendar.getInstance();
                        int startH = routine.onceAt != null
                                ? new Date(routine.onceAt).getHours()
                                : now.get(java.util.Calendar.HOUR_OF_DAY);
                        int startM = routine.onceAt != null
                                ? new Date(routine.onceAt).getMinutes()
                                : now.get(java.util.Calendar.MINUTE);
                        int startS = routine.onceAt != null
                                ? new Date(routine.onceAt).getSeconds()
                                : now.get(java.util.Calendar.SECOND);
                        showTimePicker(itemView.getContext(), startH, startM, startS, (h, m, s) -> {
                            routine.onceAt = calendarToMillis(year, month, day, h, m, s);
                            updateOnceTimeButton();
                        });
                    },
                    currentYear(), currentMonth(), currentDay()
            ).show();
        }
    }

    private static java.util.Set<Integer> parseWeekdays(String value) {
        java.util.Set<Integer> result = new java.util.LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static String joinWeekdays(java.util.Set<Integer> selected) {
        StringBuilder builder = new StringBuilder();
        for (Integer index : selected) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(index);
        }
        return builder.toString();
    }

    private static void showTimePicker(Context context, int hour, int minute, int second,
                                       TimeCallback callback) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dp(context, 24), dp(context, 12), dp(context, 24), dp(context, 12));
        NumberPicker hp = makePicker(context, 0, 23, hour);
        NumberPicker mp = makePicker(context, 0, 59, minute);
        NumberPicker sp = makePicker(context, 0, 59, second);
        layout.addView(hp);
        layout.addView(mp);
        layout.addView(sp);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.routine_choose_time)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        callback.onTime(hp.getValue(), mp.getValue(), sp.getValue()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static NumberPicker makePicker(Context context, int min, int max, int value) {
        NumberPicker picker = new NumberPicker(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        picker.setLayoutParams(lp);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return picker;
    }

    private interface TimeCallback {
        void onTime(int hour, int minute, int second);
    }

    private static long calendarToMillis(int year, int month, int day, int hour, int minute, int second) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(year, month, day, hour, minute, second);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static int currentYear() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
    }

    private static int currentMonth() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.MONTH);
    }

    private static int currentDay() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH);
    }

    private static int dp(View view, int value) {
        return Math.round(view.getResources().getDisplayMetrics().density * value);
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
