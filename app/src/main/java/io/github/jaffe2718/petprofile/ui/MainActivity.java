package io.github.jaffe2718.petprofile.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.appbar.MaterialToolbar;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.FamilyGraph;
import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.BackupManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_EXPORT = 5101;
    private static final int REQUEST_IMPORT = 5102;
    private PetRepository repository;
    private List<ProfileDetails> allDetails = new ArrayList<>();
    private List<ProfileDetails> filteredDetails = new ArrayList<>();
    private ProfileListAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private PedigreeView pedigreeView;
    private MaterialButton modeButton;
    private MaterialButton filterButton;
    private EditText searchEditText;

    private boolean listMode = true;
    private ProfileFilterDialog.FilterState activeFilter = new ProfileFilterDialog.FilterState();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repository = PetRepository.get(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.profileRecyclerView);
        emptyTextView = findViewById(R.id.emptyTextView);
        pedigreeView = findViewById(R.id.pedigreeView);
        modeButton = findViewById(R.id.modeButton);
        filterButton = findViewById(R.id.filterButton);
        searchEditText = findViewById(R.id.searchEditText);
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
        modeButton.setOnClickListener(v -> toggleMode());
        addProfileFab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfileEditActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
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
        filteredDetails = result;
        if (listMode) {
            adapter.setItems(filteredDetails);
            emptyTextView.setVisibility(filteredDetails.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
            recyclerView.setVisibility(android.view.View.VISIBLE);
            pedigreeView.setVisibility(android.view.View.GONE);
        } else {
            pedigreeView.setProfiles(filteredDetails);
        }
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
        return "nickname".equalsIgnoreCase(field.fieldKey)
                || "nickname".equalsIgnoreCase(field.fieldName)
                || "昵称".equals(field.fieldName)
                || "暱稱".equals(field.fieldName);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void toggleMode() {
        listMode = !listMode;
        if (listMode) {
            modeButton.setText(R.string.mode_list);
            recyclerView.setVisibility(android.view.View.VISIBLE);
            emptyTextView.setVisibility(filteredDetails.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
            pedigreeView.setVisibility(android.view.View.GONE);
            adapter.setItems(filteredDetails);
        } else {
            modeButton.setText(R.string.mode_pedigree);
            recyclerView.setVisibility(android.view.View.GONE);
            emptyTextView.setVisibility(android.view.View.GONE);
            pedigreeView.setVisibility(android.view.View.VISIBLE);
            pedigreeView.setProfiles(filteredDetails);
        }
    }

    private void confirmDelete(ProfileDetails details) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete_profile)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.deleteProfile(details.profile.id, new Async.EmptyResult() {
                        @Override
                        public void onSuccess() {
                            reload();
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

    private void showAbout() {
        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            versionName = "0.1.0";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_about)
                .setMessage(getString(R.string.about_version, versionName)
                        + "\n\n" + getString(R.string.about_message))
                .setPositiveButton(R.string.about_repository, (dialog, which) ->
                        openUrl("https://github.com/Jaffe2718/PetProfile"))
                .setNeutralButton(R.string.about_issues, (dialog, which) ->
                        openUrl("https://github.com/Jaffe2718/PetProfile/issues"))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
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
        new AlertDialog.Builder(this)
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
