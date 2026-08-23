package io.github.jaffe2718.petprofile.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.RoutineNotifier;
import io.github.jaffe2718.petprofile.util.TaxonomyUtil;

public class DailyTodoActivity extends AppCompatActivity {
    private static final int STATUS_ALL = 0;
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_DONE = 2;
    private static final int TIME_ALL = 0;
    private static final int TIME_REACHED = 1;
    private static final int TIME_NOTYET = 2;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final List<TodoItem> allItems = new ArrayList<>();
    private String searchQuery = "";
    private int statusFilter = STATUS_ALL;
    private int timeFilter = TIME_ALL;
    private int fromSeconds = 0;
    private int toSeconds = 23 * 3600 + 59 * 60 + 59;
    private final Set<String> selectedPets = new HashSet<>();
    private boolean sortAscending = true;

    private TodoAdapter adapter;
    private ImageView sortButton;
    private TextView emptyTextView;
    private List<ProfileEntity> petProfiles = new ArrayList<>();
    private final Map<String, String> petNicknames = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_todo);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        sortButton = findViewById(R.id.todoSortButton);
        emptyTextView = findViewById(R.id.emptyTextView);
        sortButton.setImageResource(sortAscending ? R.drawable.ic_sort_ascending : R.drawable.ic_sort_descending);
        sortButton.setOnClickListener(v -> {
            sortAscending = !sortAscending;
            sortButton.setImageResource(sortAscending ? R.drawable.ic_sort_ascending : R.drawable.ic_sort_descending);
            applyFilter();
        });

        RecyclerView recyclerView = findViewById(R.id.todoRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TodoAdapter();
        recyclerView.setAdapter(adapter);

        com.google.android.material.textfield.TextInputEditText search =
                findViewById(R.id.todoSearchEditText);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().toLowerCase(Locale.ROOT);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        findViewById(R.id.todoFilterButton).setOnClickListener(v -> showFilterDialog());
        loadTodos();
    }

    private void loadTodos() {
        Async.run(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                List<RoutineEntity> routines = db.routineDao().getEnabledRoutines();
                long now = System.currentTimeMillis();
                long todayStart = startOfToday();
                List<TodoItem> result = new ArrayList<>();
                for (RoutineEntity routine : routines) {
                    ProfileEntity profile = db.profileDao().getById(routine.profileId);
                    if (profile == null || profile.isArchived()) {
                        continue;
                    }
                    Long dueToday = computeDueToday(routine, now);
                    if (dueToday != null && routine.lastInteractionTime < todayStart) {
                        routine.lastInteractionTime = dueToday;
                        routine.completed = false;
                        db.routineDao().update(routine);
                    }
                    boolean relevant = isRelevant(routine, dueToday, now, todayStart);
                    if (!relevant) {
                        continue;
                    }
                    List<ProfileCustomFieldEntity> fields = db.profileDao().getCustomFields(routine.profileId);
                    TodoItem item = new TodoItem();
                    item.routine = routine;
                    item.profile = profile;
                    item.title = routine.title;
                    item.details = routine.details;
                    item.dueTime = dueToday != null ? dueToday : routine.lastInteractionTime;
                    item.completed = routine.completed;
                    item.nickname = findNickname(fields);
                    item.taxonomy = TaxonomyUtil.speciesDisplay(profile);
                    item.subtitle = subtitle(item.nickname, profile.gender);
                    result.add(item);
                }
                List<ProfileEntity> profiles = new ArrayList<>(db.profileDao().getAllProfiles());
                List<ProfileEntity> active = new ArrayList<>();
                Map<String, String> nicknameById = new HashMap<>();
                for (ProfileEntity p : profiles) {
                    if (!p.isArchived()) {
                        active.add(p);
                        nicknameById.put(p.id, findNickname(db.profileDao().getCustomFields(p.id)));
                    }
                }
                Async.ui(() -> {
                    petProfiles.clear();
                    petProfiles.addAll(active);
                    petNicknames.clear();
                    petNicknames.putAll(nicknameById);
                    allItems.clear();
                    allItems.addAll(result);
                    applyFilter();
                });
            } catch (Throwable ignored) {
            }
        });
    }

    private boolean isRelevant(RoutineEntity routine, Long dueToday, long now, long todayStart) {
        if (!routine.completed) {
            if (dueToday != null) {
                return true;
            }
            if (RoutineEntity.POLICY_CARRY.equals(routine.policy)) {
                return RoutineEntity.TYPE_WEEKLY.equals(routine.type)
                        || (routine.onceAt != null && routine.onceAt <= now);
            }
            return false;
        }
        return routine.lastInteractionTime >= todayStart;
    }

    private Long computeDueToday(RoutineEntity routine, long now) {
        if (RoutineEntity.TYPE_WEEKLY.equals(routine.type)) {
            Set<Integer> weekdays = parseWeekdays(routine.weekdays);
            if (weekdays.isEmpty()) {
                for (int i = 0; i < 7; i++) {
                    weekdays.add(i);
                }
            }
            Calendar nowCal = Calendar.getInstance();
            int todayIndex = nowCal.get(Calendar.DAY_OF_WEEK) - 1;
            if (!weekdays.contains(todayIndex)) {
                return null;
            }
            Calendar due = Calendar.getInstance();
            due.set(Calendar.HOUR_OF_DAY, routine.hour);
            due.set(Calendar.MINUTE, routine.minute);
            due.set(Calendar.SECOND, routine.second);
            due.set(Calendar.MILLISECOND, 0);
            return due.getTimeInMillis();
        } else {
            if (routine.onceAt == null) {
                return null;
            }
            Calendar once = Calendar.getInstance();
            once.setTimeInMillis(routine.onceAt);
            Calendar today = Calendar.getInstance();
            if (once.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && once.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                return routine.onceAt;
            }
            return null;
        }
    }

    private long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static Set<Integer> parseWeekdays(String value) {
        Set<Integer> result = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String part : value.split(",")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static String findNickname(List<ProfileCustomFieldEntity> fields) {
        if (fields != null) {
            for (ProfileCustomFieldEntity field : fields) {
                if ("nickname".equalsIgnoreCase(field.fieldKey)
                        || "nickname".equalsIgnoreCase(field.fieldName)
                        || "昵称".equals(field.fieldName)
                        || "暱稱".equals(field.fieldName)) {
                    return field.textValue == null ? "" : field.textValue.trim();
                }
            }
        }
        return "";
    }

    private static String subtitle(String nickname, String gender) {
        StringBuilder builder = new StringBuilder();
        if (nickname != null && !nickname.isEmpty()) {
            builder.append(nickname);
        }
        String symbol = genderSymbol(gender);
        if (!symbol.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("·");
            }
            builder.append(symbol);
        }
        return builder.toString();
    }

    private static String genderSymbol(String gender) {
        if ("MALE".equals(gender)) {
            return "♂";
        } else if ("FEMALE".equals(gender)) {
            return "♀";
        }
        return "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allItems.isEmpty()) {
            loadTodos();
        }
    }

    private void applyFilter() {
        long now = System.currentTimeMillis();
        List<TodoItem> shown = new ArrayList<>();
        for (TodoItem item : allItems) {
            if (statusFilter == STATUS_PENDING && item.completed) {
                continue;
            }
            if (statusFilter == STATUS_DONE && !item.completed) {
                continue;
            }
            if (timeFilter == TIME_REACHED && now < item.dueTime) {
                continue;
            }
            if (timeFilter == TIME_NOTYET && now >= item.dueTime) {
                continue;
            }
            if (!selectedPets.isEmpty() && !selectedPets.contains(item.profile.id)) {
                continue;
            }
            int seconds = secondsOfDay(item.dueTime);
            if (seconds < fromSeconds || seconds > toSeconds) {
                continue;
            }
            if (!searchQuery.isEmpty()) {
                String haystack = (item.title + " " + item.details + " " + item.nickname
                        + " " + item.subtitle + " " + item.taxonomy).toLowerCase(Locale.ROOT);
                if (!haystack.contains(searchQuery)) {
                    continue;
                }
            }
            shown.add(item);
        }
        Collections.sort(shown, new Comparator<TodoItem>() {
            @Override
            public int compare(TodoItem a, TodoItem b) {
                int r = Long.compare(a.dueTime, b.dueTime);
                return sortAscending ? r : -r;
            }
        });
        adapter.setData(shown);
        emptyTextView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static int secondsOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.HOUR_OF_DAY) * 3600
                + c.get(Calendar.MINUTE) * 60
                + c.get(Calendar.SECOND);
    }

    private void showFilterDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_todo_filter, null);
        MaterialButton fromButton = view.findViewById(R.id.filterFromButton);
        MaterialButton toButton = view.findViewById(R.id.filterToButton);
        RadioGroup statusGroup = view.findViewById(R.id.filterStatusGroup);
        RadioGroup timeGroup = view.findViewById(R.id.filterTimeGroup);
        LinearLayout petContainer = view.findViewById(R.id.filterPetContainer);
        TextView petLabel = view.findViewById(R.id.filterPetLabel);
        android.widget.HorizontalScrollView petScroll = view.findViewById(R.id.filterPetScroll);

        fromButton.setText(getString(R.string.filter_from) + " " + timeOfDay(fromSeconds));
        toButton.setText(getString(R.string.filter_to) + " " + timeOfDay(toSeconds));

        int radioId;
        if (statusFilter == STATUS_PENDING) {
            radioId = R.id.filterStatusPending;
        } else if (statusFilter == STATUS_DONE) {
            radioId = R.id.filterStatusDone;
        } else {
            radioId = R.id.filterStatusAll;
        }
        statusGroup.check(radioId);

        int timeRadioId;
        if (timeFilter == TIME_REACHED) {
            timeRadioId = R.id.filterTimeReached;
        } else if (timeFilter == TIME_NOTYET) {
            timeRadioId = R.id.filterTimeNotyet;
        } else {
            timeRadioId = R.id.filterTimeAll;
        }
        timeGroup.check(timeRadioId);

        fromButton.setOnClickListener(v ->
                pickTime(fromSeconds, s -> {
                    fromSeconds = s;
                    fromButton.setText(getString(R.string.filter_from) + " " + timeOfDay(s));
                }));
        toButton.setOnClickListener(v ->
                pickTime(toSeconds, s -> {
                    toSeconds = s;
                    toButton.setText(getString(R.string.filter_to) + " " + timeOfDay(s));
                }));

        petContainer.removeAllViews();
        for (ProfileEntity profile : petProfiles) {
            Chip chip = new Chip(this);
            chip.setCheckable(true);
            chip.setChecked(selectedPets.contains(profile.id));
            String nickname = petNicknames.getOrDefault(profile.id, "");
            if (nickname.isEmpty()) {
                nickname = TaxonomyUtil.speciesDisplay(profile);
            }
            if (nickname.isEmpty()) {
                nickname = profile.id;
            }
            String symbol = genderSymbol(profile.gender);
            chip.setText(symbol.isEmpty() ? nickname : nickname + " " + symbol);
            chip.setChipIconSize(dp(24));
            chip.setEnsureMinTouchTargetSize(false);
            if (profile.avatarUri != null && !profile.avatarUri.trim().isEmpty()) {
                RoundedBitmapDrawable icon = loadRoundedAvatar(profile.avatarUri);
                if (icon != null) {
                    chip.setChipIcon(icon);
                }
            }
            chip.setTag(profile.id);
            chip.setOnCheckedChangeListener((c, checked) -> {
                String id = (String) c.getTag();
                if (checked) {
                    selectedPets.add(id);
                } else {
                    selectedPets.remove(id);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            petContainer.addView(chip, lp);
        }
        if (petProfiles.isEmpty()) {
            petLabel.setVisibility(View.GONE);
            petScroll.setVisibility(View.GONE);
        } else {
            petLabel.setVisibility(View.VISIBLE);
            petScroll.setVisibility(View.VISIBLE);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_filter)
                .setView(view)
                .setPositiveButton(R.string.filter_apply, (d, w) -> {
                    int checkedId = statusGroup.getCheckedRadioButtonId();
                    if (checkedId == R.id.filterStatusPending) {
                        statusFilter = STATUS_PENDING;
                    } else if (checkedId == R.id.filterStatusDone) {
                        statusFilter = STATUS_DONE;
                    } else {
                        statusFilter = STATUS_ALL;
                    }
                    int timeId = timeGroup.getCheckedRadioButtonId();
                    if (timeId == R.id.filterTimeReached) {
                        timeFilter = TIME_REACHED;
                    } else if (timeId == R.id.filterTimeNotyet) {
                        timeFilter = TIME_NOTYET;
                    } else {
                        timeFilter = TIME_ALL;
                    }
                    applyFilter();
                })
                .setNeutralButton(R.string.filter_reset, (d, w) -> {
                    fromSeconds = 0;
                    toSeconds = 23 * 3600 + 59 * 60 + 59;
                    statusFilter = STATUS_ALL;
                    timeFilter = TIME_ALL;
                    selectedPets.clear();
                    applyFilter();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static String timeOfDay(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    private void pickTime(int currentSeconds, TimeCallback callback) {
        int h = currentSeconds / 3600;
        int m = (currentSeconds % 3600) / 60;
        int s = currentSeconds % 60;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(24);
        layout.setPadding(pad, dp(12), pad, dp(12));
        android.widget.NumberPicker hp = makePicker(0, 23, h);
        android.widget.NumberPicker mp = makePicker(0, 59, m);
        android.widget.NumberPicker sp = makePicker(0, 59, s);
        layout.addView(hp);
        layout.addView(mp);
        layout.addView(sp);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.routine_choose_time)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        callback.onTime(hp.getValue() * 3600 + mp.getValue() * 60 + sp.getValue()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private android.widget.NumberPicker makePicker(int min, int max, int value) {
        android.widget.NumberPicker picker = new android.widget.NumberPicker(this);
        picker.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return picker;
    }

    private RoundedBitmapDrawable loadRoundedAvatar(String uri) {
        try {
            Bitmap bitmap = null;
            if (uri.startsWith("content://") || uri.startsWith("file://")) {
                try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
            } else {
                bitmap = BitmapFactory.decodeFile(uri);
            }
            if (bitmap == null) {
                return null;
            }
            RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
            drawable.setCircular(true);
            drawable.setAntiAlias(true);
            return drawable;
        } catch (Throwable t) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private interface TimeCallback {
        void onTime(int seconds);
    }

    private class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.Holder> {
        private final List<TodoItem> data = new ArrayList<>();

        void setData(List<TodoItem> items) {
            data.clear();
            data.addAll(items);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_todo, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            private final MaterialCardView card;
            private final ImageView avatar;
            private final TextView title;
            private final TextView time;
            private final TextView subtitle;
            private final TextView taxonomy;
            private final TextView details;
            private final CheckBox checkbox;

            Holder(View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.todoCard);
                avatar = itemView.findViewById(R.id.todoAvatar);
                title = itemView.findViewById(R.id.todoTitle);
                time = itemView.findViewById(R.id.todoTime);
                subtitle = itemView.findViewById(R.id.todoSubtitle);
                taxonomy = itemView.findViewById(R.id.todoTaxonomy);
                details = itemView.findViewById(R.id.todoDetails);
                checkbox = itemView.findViewById(R.id.todoCheckbox);
                checkbox.setOnClickListener(v -> {
                    TodoItem item = (TodoItem) itemView.getTag();
                    if (item == null) {
                        return;
                    }
                    boolean checked = checkbox.isChecked();
                    onToggle(item, checked);
                });
            }

            void bind(TodoItem item) {
                long now = System.currentTimeMillis();
                itemView.setTag(item);
                title.setText(item.title);
                time.setText(TIME_FORMAT.format(new Date(item.dueTime)));
                subtitle.setText(item.subtitle);
                taxonomy.setText(item.taxonomy);
                details.setText(item.details);
                checkbox.setChecked(item.completed);
                checkbox.setEnabled(item.completed || now >= item.dueTime);

                if (item.profile.avatarUri != null && !item.profile.avatarUri.trim().isEmpty()) {
                    Glide.with(avatar).load(item.profile.avatarUri).into(avatar);
                } else {
                    avatar.setImageResource(android.R.drawable.ic_menu_gallery);
                }

                int bg;
                int text;
                if (item.completed) {
                    bg = R.color.profile_archived_bg;
                    text = R.color.text_secondary;
                } else if (now >= item.dueTime) {
                    bg = R.color.record_archive_bg;
                    text = R.color.text_primary;
                } else {
                    bg = R.color.record_daily_bg;
                    text = R.color.text_primary;
                }
                card.setCardBackgroundColor(getColor(bg));
                title.setTextColor(getColor(text));
                subtitle.setTextColor(getColor(text));
                details.setTextColor(getColor(text));
                taxonomy.setTextColor(getColor(R.color.text_secondary));
            }
        }
    }

    private void onToggle(TodoItem item, boolean checked) {
        item.completed = checked;
        item.routine.completed = checked;
        item.routine.lastInteractionTime = System.currentTimeMillis();
        Async.run(() -> {
            try {
                AppDatabase.getInstance(this).routineDao().update(item.routine);
            } catch (Throwable ignored) {
            }
        });
        RoutineNotifier.sync(this);
        applyFilter();
    }

    private static class TodoItem {
        RoutineEntity routine;
        ProfileEntity profile;
        String title = "";
        String details = "";
        String nickname = "";
        String subtitle = "";
        String taxonomy = "";
        long dueTime;
        boolean completed;
    }
}
