package io.github.jaffe2718.petprofile.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.os.LocaleListCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.KeeperInfo;
import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.mcp.McpServer;
import io.github.jaffe2718.petprofile.mcp.McpService;
import io.github.jaffe2718.petprofile.mcp.McpTokenManager;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.BackupManager;
import io.github.jaffe2718.petprofile.util.KeeperInfoManager;
import io.github.jaffe2718.petprofile.util.LocationHelper;
import io.github.jaffe2718.petprofile.util.OemPermissionHelper;
import io.github.jaffe2718.petprofile.util.RoutineNotifier;
import io.github.jaffe2718.petprofile.util.TaxonomyUtil;
import io.github.jaffe2718.petprofile.util.RoutineScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_EXPORT = 5101;
    private static final int REQUEST_IMPORT = 5102;
    private static final int REQUEST_KEEPER_MAP = 5103;
    private static final String MCP_PREFS = "pet_profile_mcp";
    private static final String MCP_ENABLED = "mcp_enabled";
    private final BroadcastReceiver dataChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };
    private PetRepository repository;
    private List<ProfileDetails> allDetails = new ArrayList<>();
    private List<ProfileDetails> filteredDetails = new ArrayList<>();
    private ProfileListAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private FrameLayout contentFrame;
    private View searchBar;
    private PedigreeView pedigreeView;
    private float searchBarCollapsedOffset;
    private int searchBarHeight = -1;
    private float lastTouchY;
    private boolean touchActive;
    private ImageButton modeButton;
    private ImageButton filterButton;
    private ImageButton sortButton;
    private EditText searchEditText;
    private boolean sortAscending;
    private View keeperInfoDialogView;
    private KeeperInfo draftKeeperInfo = new KeeperInfo();

    private boolean listMode = true;
    private ProfileFilterDialog.FilterState activeFilter = new ProfileFilterDialog.FilterState();

    private boolean isMcpEnabled() {
        SharedPreferences prefs = getSharedPreferences(MCP_PREFS, MODE_PRIVATE);
        return prefs.getBoolean(MCP_ENABLED, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repository = PetRepository.get(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.profileRecyclerView);
        emptyTextView = findViewById(R.id.emptyTextView);
        contentFrame = findViewById(R.id.contentFrame);
        pedigreeView = findViewById(R.id.pedigreeView);
        modeButton = findViewById(R.id.modeButton);
        filterButton = findViewById(R.id.filterButton);
        sortButton = findViewById(R.id.sortButton);
        searchEditText = findViewById(R.id.searchEditText);
        searchBar = findViewById(R.id.searchBar);
        FloatingActionButton addProfileFab = findViewById(R.id.addProfileFab);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProfileListAdapter(new ProfileListAdapter.Listener() {
            @Override
            public void onOpen(ProfileDetails details) {
                Intent intent = new Intent(MainActivity.this, RecordListActivity.class);
                intent.putExtra(RecordListActivity.EXTRA_PROFILE_ID, details.profile.id);
                startActivity(intent);
            }

            @Override
            public void onEdit(ProfileDetails details) {
                Intent intent = new Intent(MainActivity.this, ProfileEditActivity.class);
                intent.putExtra(ProfileEditActivity.EXTRA_PROFILE_ID, details.profile.id);
                startActivity(intent);
            }

            @Override
            public void onDelete(ProfileDetails details) {
                confirmDelete(details);
            }
        });
        recyclerView.setAdapter(adapter);
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView recyclerView, android.view.MotionEvent event) {
                handleSearchBarTouch(event);
                return false;
            }

            @Override
            public void onTouchEvent(RecyclerView recyclerView, android.view.MotionEvent event) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        filterButton.setOnClickListener(v -> showFilterDialog());
        sortButton.setOnClickListener(v -> toggleSortOrder());
        modeButton.setOnClickListener(v -> toggleMode());
        addProfileFab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfileEditActivity.class);
            startActivity(intent);
        });
        updateSortButton();
        updateModeUi();
        requestNotificationPermission();
        maybePromptAutoStart();
    }

    private void maybePromptAutoStart() {
        if (OemPermissionHelper.isIgnoringBatteryOptimizations(this)) {
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        if (prefs.getBoolean("autostart_prompted", false)) {
            return;
        }
        prefs.edit().putBoolean("autostart_prompted", true).apply();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.autostart_title)
                .setMessage(R.string.autostart_message)
                .setPositiveButton(R.string.autostart_grant, (d, w) -> OemPermissionHelper.openAutoStartSettings(this))
                .setNegativeButton(R.string.autostart_later, null)
                .show();
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        RoutineScheduler.scheduleAll(this);
        RoutineScheduler.scheduleDailyRefresh(this);
        RoutineNotifier.sync(this);
        if (isMcpEnabled() && !McpServer.get(this).isRunning()) {
            McpService.start(this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerDataChangeReceiver();
    }

    @Override
    protected void onStop() {
        unregisterDataChangeReceiver();
        super.onStop();
    }

    @SuppressLint("UnprotectedBroadcastReceiver")
    private void registerDataChangeReceiver() {
        IntentFilter filter = new IntentFilter(McpServer.ACTION_DATA_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dataChangeReceiver, filter);
        }
    }

    private void unregisterDataChangeReceiver() {
        try {
            unregisterReceiver(dataChangeReceiver);
        } catch (Throwable ignored) {
        }
    }

    private void showFilterDialog() {
        ProfileFilterDialog.show(this, allDetails, activeFilter, state -> {
            activeFilter = state;
            applyFilter();
        });
    }

    private void reload() {
        repository.getAllProfileDetails(new AsyncResultAdapter<List<ProfileDetails>>() {
            @Override
            public void onSuccess(List<ProfileDetails> value) {
                allDetails.clear();
                allDetails.addAll(value);
                allDetails.sort((a, b) -> Long.compare(lastRecordTime(b), lastRecordTime(a)));
                applyFilter();
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilter() {
        String query = searchEditText == null ? "" : searchEditText.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<ProfileDetails> result = new ArrayList<>();
        for (ProfileDetails details : allDetails) {
            if (!matchesSearch(details, query)) continue;
            if (!activeFilter.matches(details)) continue;
            result.add(details);
        }
        result.sort((a, b) -> {
            int comparison = Long.compare(lastRecordTime(a), lastRecordTime(b));
            return sortAscending ? comparison : -comparison;
        });
        filteredDetails = result;
        updateModeUi();
    }

    private void toggleSortOrder() {
        sortAscending = !sortAscending;
        updateSortButton();
        applyFilter();
    }

    private void updateSortButton() {
        sortButton.setImageResource(sortAscending
                ? R.drawable.ic_sort_ascending
                : R.drawable.ic_sort_descending);
        sortButton.setContentDescription(getString(sortAscending
                ? R.string.sort_ascending
                : R.string.sort_descending));
    }

    private long lastRecordTime(ProfileDetails details) {
        if (details.lastRecordTimestamp != null) {
            return details.lastRecordTimestamp;
        }
        if (details.establishmentTimestamp != null) {
            return details.establishmentTimestamp;
        }
        return details.profile.createdAt;
    }

    private boolean matchesSearch(ProfileDetails details, String query) {
        if (query.isEmpty()) return true;
        String nickname = findNickname(details);
        if (containsIgnoreCase(nickname, query)) return true;
        for (ProfileCustomFieldEntity field : details.customFields) {
            if (!isNicknameField(field)) continue;
            String value = field.textValue == null ? "" : field.textValue;
            if (containsIgnoreCase(value, query)) return true;
        }
        ProfileEntity profile = details.profile;
        if (containsIgnoreCase(profile.kingdom, query)
                || containsIgnoreCase(profile.phylum, query)
                || containsIgnoreCase(profile.taxClass, query)
                || containsIgnoreCase(profile.taxOrder, query)
                || containsIgnoreCase(profile.family, query)
                || containsIgnoreCase(profile.genus, query)
                || containsIgnoreCase(profile.species, query)
                || containsIgnoreCase(profile.subspecies, query)) {
            return true;
        }
        return false;
    }

    private String findNickname(ProfileDetails details) {
        for (ProfileCustomFieldEntity field : details.customFields) {
            if (isNicknameField(field)) {
                return field.textValue == null ? "" : field.textValue;
            }
        }
        return "";
    }

    private boolean isNicknameField(ProfileCustomFieldEntity field) {
        return TaxonomyUtil.isNickname(field);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void toggleMode() {
        listMode = !listMode;
        updateModeUi();
    }

    private void updateModeUi() {
        sortButton.setVisibility(listMode ? View.VISIBLE : View.GONE);
        modeButton.setImageResource(listMode ? R.drawable.ic_mode_list : R.drawable.ic_mode_pedigree);
        modeButton.setContentDescription(getString(listMode ? R.string.mode_list : R.string.mode_pedigree));
        if (listMode) {
            recyclerView.setVisibility(android.view.View.VISIBLE);
            boolean empty = filteredDetails.isEmpty();
            contentFrame.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
            emptyTextView.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
            pedigreeView.setVisibility(android.view.View.GONE);
            adapter.setItems(filteredDetails);
        } else {
            resetSearchBar();
            recyclerView.setVisibility(android.view.View.GONE);
            contentFrame.setVisibility(android.view.View.VISIBLE);
            emptyTextView.setVisibility(android.view.View.GONE);
            pedigreeView.setVisibility(android.view.View.VISIBLE);
            pedigreeView.setProfiles(filteredDetails);
        }
    }

    private void resetSearchBar() {
        if (searchBar == null) {
            return;
        }
        if (searchBarHeight <= 0) {
            searchBarHeight = searchBar.getHeight();
        }
        searchBarCollapsedOffset = 0f;
        ViewGroup.LayoutParams params = searchBar.getLayoutParams();
        params.height = searchBarHeight > 0 ? searchBarHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
        searchBar.setLayoutParams(params);
    }

    private void applySearchBarScroll(float dy) {
        if (searchBar == null || !listMode) {
            return;
        }
        if (searchBarHeight <= 0) {
            searchBarHeight = searchBar.getHeight();
        }
        if (searchBarHeight <= 0) {
            return;
        }
        searchBarCollapsedOffset += dy;
        searchBarCollapsedOffset = Math.max(0f, Math.min(searchBarHeight, searchBarCollapsedOffset));
        int newHeight = Math.max(0, Math.round(searchBarHeight - searchBarCollapsedOffset));
        ViewGroup.LayoutParams params = searchBar.getLayoutParams();
        if (params.height != newHeight) {
            params.height = newHeight;
            searchBar.setLayoutParams(params);
        }
    }

    private void handleSearchBarTouch(android.view.MotionEvent event) {
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                lastTouchY = event.getRawY();
                touchActive = true;
                break;
            case android.view.MotionEvent.ACTION_MOVE:
                if (touchActive) {
                    float delta = lastTouchY - event.getRawY();
                    lastTouchY = event.getRawY();
                    applySearchBarScroll(delta);
                }
                break;
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                touchActive = false;
                break;
            default:
                break;
        }
    }

    private void confirmDelete(ProfileDetails details) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.confirm_delete_profile)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.deleteProfile(details.profile.id, new Async.EmptyResult() {
                        @Override
                        public void onSuccess() {
                            reload();
                            RoutineNotifier.sync(MainActivity.this);
                        }

                        @Override
                        public void onError(Throwable error) {
                            Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_keeper_info) {
            showKeeperInfoDialog();
            return true;
        }
        if (id == R.id.action_daily_todo) {
            startActivity(new Intent(this, DailyTodoActivity.class));
            return true;
        }
        if (id == R.id.action_export) {
            exportZip();
            return true;
        }
        if (id == R.id.action_import) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            startActivityForResult(intent, REQUEST_IMPORT);
            return true;
        }
        if (id == R.id.action_scan_qr) {
            startActivity(new Intent(this, QrScannerActivity.class));
            return true;
        }
        if (id == R.id.action_language) {
            chooseLanguage();
            return true;
        }
        if (id == R.id.action_about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showKeeperInfoDialog() {
        keeperInfoDialogView = getLayoutInflater().inflate(R.layout.dialog_keeper_info, null);
        EditText nicknameEditText = keeperInfoDialogView.findViewById(R.id.keeperNicknameEditText);
        Button homeButton = keeperInfoDialogView.findViewById(R.id.keeperHomeButton);
        draftKeeperInfo = KeeperInfoManager.load(this);
        nicknameEditText.setText(draftKeeperInfo.nickname);
        updateKeeperHomeButton(homeButton);
        homeButton.setOnClickListener(v -> pickKeeperHome());

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_keeper_info)
                .setView(keeperInfoDialogView)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    draftKeeperInfo.nickname = nicknameEditText.getText().toString().trim();
                    KeeperInfoManager.save(this, draftKeeperInfo);
                    keeperInfoDialogView = null;
                })
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> keeperInfoDialogView = null)
                .setOnDismissListener(dialog -> keeperInfoDialogView = null)
                .show();
    }

    private void pickKeeperHome() {
        double[] coords = LocationHelper.lastKnownCoordinates(this);
        double initialLatitude = coords == null ? 35.0 : coords[0];
        double initialLongitude = coords == null ? 105.0 : coords[1];
        LocationHelper.openMapPicker(this, REQUEST_KEEPER_MAP, initialLatitude, initialLongitude);
    }

    private void updateKeeperHomeButton(Button homeButton) {
        if (homeButton == null) return;
        if (draftKeeperInfo.hasHomePlace()) {
            homeButton.setText(draftKeeperInfo.homePlace);
        } else {
            homeButton.setText("");
        }
    }

    private void showAbout() {
        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            versionName = "0.1.0";
        }
        new MaterialAlertDialogBuilder(this)
                .setCustomTitle(buildAboutTitle())
                .setMessage(getString(R.string.about_version, versionName)
                        + "\n\n" + getString(R.string.about_message))
                .setPositiveButton(R.string.about_repository, (dialog, which) ->
                        openUrl("https://github.com/Jaffe2718/PetProfile"))
                .setNeutralButton(R.string.about_issues, (dialog, which) ->
                        openUrl("https://github.com/Jaffe2718/PetProfile/issues"))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private View buildAboutTitle() {
        View titleView = getLayoutInflater().inflate(R.layout.view_about_title, null);
        ImageButton mcpButton = titleView.findViewById(R.id.aboutMcpButton);
        mcpButton.setOnClickListener(v -> showMcpDialog());
        return titleView;
    }

    private Dialog mcpDialog;

    private void showMcpDialog() {
        if (mcpDialog != null && mcpDialog.isShowing()) {
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_mcp, null);
        Dialog dialog = new Dialog(this);
        dialog.setContentView(view);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }

        MaterialSwitch mcpSwitch = view.findViewById(R.id.mcpSwitch);
        TextView urlText = view.findViewById(R.id.mcpUrlText);
        TextView keyText = view.findViewById(R.id.mcpKeyText);
        ImageButton refreshButton = view.findViewById(R.id.mcpRefreshButton);
        ImageButton copyButton = view.findViewById(R.id.mcpCopyButton);
        ImageButton urlCopyButton = view.findViewById(R.id.mcpUrlCopyButton);

        McpServer server = McpServer.get(this);
        mcpSwitch.setChecked(server.isRunning());
        bindMcpFields(server, urlText, keyText);

        mcpSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(MCP_PREFS, MODE_PRIVATE).edit().putBoolean(MCP_ENABLED, isChecked).apply();
            if (isChecked) {
                McpService.start(this);
            } else {
                McpService.stop(this);
            }
            bindMcpFields(McpServer.get(this), urlText, keyText);
        });

        refreshButton.setOnClickListener(v -> {
            McpTokenManager.refreshToken(this);
            keyText.setText(McpTokenManager.getToken(this));
            Toast.makeText(this, R.string.mcp_refresh, Toast.LENGTH_SHORT).show();
        });

        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("PetProfile MCP token", keyText.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.mcp_copied, Toast.LENGTH_SHORT).show();
        });

        urlCopyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("PetProfile MCP URL", urlText.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.mcp_copied, Toast.LENGTH_SHORT).show();
        });

        mcpDialog = dialog;
        dialog.show();
    }

    private void bindMcpFields(McpServer server, TextView urlText, TextView keyText) {
        keyText.setText(server.getAuthToken());
        if (server.isRunning()) {
            urlText.setText(server.getUrl());
        } else {
            urlText.setText(getString(R.string.mcp_disabled));
        }
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void exportZip() {
        repository.exportAll(new AsyncResultAdapter<ExportBundle>() {
            @Override
            public void onSuccess(ExportBundle value) {
                pendingExportBundle = value;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE, "pet-profile-backup.zip");
                startActivityForResult(intent, REQUEST_EXPORT);
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private ExportBundle pendingExportBundle;

    private void chooseLanguage() {
        String[] tags = {"zh-CN", "zh-HK", "en-US", "ja-JP"};
        String[] labels = {"简体中文", "繁體中文", "English", "日本語"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Language / 語言 / 语言")
                .setItems(labels, (dialog, which) -> {
                    LocaleListCompat locales = LocaleListCompat.forLanguageTags(tags[which]);
                    AppCompatDelegate.setApplicationLocales(locales);
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_KEEPER_MAP) {
            double pickedLatitude = data.getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LATITUDE, 0.0);
            double pickedLongitude = data.getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LONGITUDE, 0.0);
            draftKeeperInfo.latitude = pickedLatitude;
            draftKeeperInfo.longitude = pickedLongitude;
            LocationHelper.resolveAddress(this, pickedLatitude, pickedLongitude, new LocationHelper.Callback() {
                @Override
                public void onResult(LocationHelper.LocationResult result) {
                    draftKeeperInfo.homePlace = result.name + " "
                            + LocationHelper.formatDms(result.latitude, true)
                            + ", " + LocationHelper.formatDms(result.longitude, false);
                    updateKeeperHomeButton(keeperInfoDialogView == null ? null
                            : keeperInfoDialogView.findViewById(R.id.keeperHomeButton));
                }

                @Override
                public void onError(String message) {
                    draftKeeperInfo.homePlace = LocationHelper.formatDms(pickedLatitude, true)
                            + ", " + LocationHelper.formatDms(pickedLongitude, false);
                    updateKeeperHomeButton(keeperInfoDialogView == null ? null
                            : keeperInfoDialogView.findViewById(R.id.keeperHomeButton));
                }
            });
            return;
        }
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQUEST_EXPORT && pendingExportBundle != null) {
            AsyncResultAdapter<String> callback = new AsyncResultAdapter<String>() {
                @Override
                public void onSuccess(String value) {
                    Toast.makeText(MainActivity.this, R.string.exported, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Throwable error) {
                    Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
            };
            Async.run(() -> {
                try {
                    BackupManager.exportZip(MainActivity.this, pendingExportBundle, uri);
                    Async.ui(() -> callback.onSuccess("ok"));
                } catch (Throwable t) {
                    Async.ui(() -> callback.onError(t));
                }
            });
        } else if (requestCode == REQUEST_IMPORT) {
            Async.run(() -> {
                try {
                    ExportBundle bundle = BackupManager.readZip(MainActivity.this, uri);
                    repository.importBundle(bundle, new Async.EmptyResult() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(MainActivity.this, R.string.imported, Toast.LENGTH_SHORT).show();
                            RoutineScheduler.scheduleAll(MainActivity.this);
                            RoutineNotifier.sync(MainActivity.this);
                            reload();
                        }

                        @Override
                        public void onError(Throwable error) {
                            Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Throwable t) {
                    Async.ui(() -> Toast.makeText(MainActivity.this, t.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    private abstract class AsyncResultAdapter<T> implements Async.Result<T> {
    }
}
