package io.github.jaffe2718.petprofile.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.KeeperInfo;
import io.github.jaffe2718.petprofile.data.LanTransferPayload;
import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.CardShareManager;
import io.github.jaffe2718.petprofile.util.LanTransferServer;
import io.github.jaffe2718.petprofile.util.NetworkUtil;
import io.github.jaffe2718.petprofile.util.QrCodeUtil;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.HashMap;

public class RecordListActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_ID = "profile_id";

    private PetRepository repository;
    private String profileId;
    private RecordAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private LanTransferServer activeTransferServer;

    private View searchBar;
    private EditText searchEditText;
    private ImageButton filterButton;
    private float searchBarCollapsedOffset;
    private int searchBarHeight = -1;
    private float lastTouchY;
    private boolean touchActive;

    private List<RecordEntity> allRecords = new ArrayList<>();
    private Map<String, List<String>> imagesByRecord = new HashMap<>();
    private Map<String, List<RecordFieldEntity>> fieldsByRecord = new HashMap<>();
    private RecordFilterDialog.FilterState activeFilter = new RecordFilterDialog.FilterState();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_list);
        repository = PetRepository.get(this);
        profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        if (profileId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(R.string.label_records);

        recyclerView = findViewById(R.id.recordRecyclerView);
        emptyTextView = findViewById(R.id.emptyTextView);
        searchBar = findViewById(R.id.recordSearchBar);
        searchEditText = findViewById(R.id.recordSearchEditText);
        filterButton = findViewById(R.id.recordFilterButton);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override
            public void onOpen(RecordEntity record) {
                Intent intent = new Intent(RecordListActivity.this, RecordDetailActivity.class);
                intent.putExtra(RecordDetailActivity.EXTRA_PROFILE_ID, profileId);
                intent.putExtra(RecordDetailActivity.EXTRA_RECORD_ID, record.id);
                startActivity(intent);
            }

            @Override
            public void onDelete(RecordEntity record) {
                confirmDelete(record);
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

        FloatingActionButton addRecordFab = findViewById(R.id.addRecordFab);
        addRecordFab.setOnClickListener(v -> {
            Intent intent = new Intent(RecordListActivity.this, RecordEditActivity.class);
            intent.putExtra(RecordEditActivity.EXTRA_PROFILE_ID, profileId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        repository.getRecords(profileId, new Async.Result<List<RecordEntity>>() {
            @Override
            public void onSuccess(List<RecordEntity> value) {
                allRecords = value == null ? new ArrayList<>() : value;
                List<String> recordIds = new ArrayList<>();
                for (RecordEntity record : allRecords) {
                    recordIds.add(record.id);
                }
                repository.getRecordImages(recordIds, new Async.Result<Map<String, List<String>>>() {
                    @Override
                    public void onSuccess(Map<String, List<String>> imageMap) {
                        imagesByRecord = imageMap == null ? new HashMap<>() : imageMap;
                        repository.getRecordFields(recordIds, new Async.Result<Map<String, List<RecordFieldEntity>>>() {
                            @Override
                            public void onSuccess(Map<String, List<RecordFieldEntity>> fieldMap) {
                                fieldsByRecord = fieldMap == null ? new HashMap<>() : fieldMap;
                                applyFilter();
                            }

                            @Override
                            public void onError(Throwable error) {
                                fieldsByRecord = new HashMap<>();
                                applyFilter();
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        imagesByRecord = new HashMap<>();
                        fieldsByRecord = new HashMap<>();
                        applyFilter();
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showFilterDialog() {
        RecordFilterDialog.show(this, collectFieldNames(fieldsByRecord), activeFilter, state -> {
            activeFilter = state;
            applyFilter();
        });
    }

    private List<String> collectFieldNames(Map<String, List<RecordFieldEntity>> fieldMap) {
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<String, List<RecordFieldEntity>> entry : fieldMap.entrySet()) {
            for (RecordFieldEntity field : entry.getValue()) {
                if (field.fieldName != null && !field.fieldName.trim().isEmpty()) {
                    names.add(field.fieldName.trim());
                } else if (field.fieldKey != null && !field.fieldKey.trim().isEmpty()) {
                    names.add(field.fieldKey.trim());
                }
            }
        }
        return new ArrayList<>(names);
    }

    private void applyFilter() {
        String query = searchEditText == null
                ? ""
                : searchEditText.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<RecordEntity> result = new ArrayList<>();
        for (RecordEntity record : allRecords) {
            if (!matchesSearch(record, query)) {
                continue;
            }
            if (!activeFilter.matches(record, fieldsByRecord)) {
                continue;
            }
            result.add(record);
        }
        adapter.setItems(result, imagesByRecord);
        emptyTextView.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesSearch(RecordEntity record, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (containsIgnoreCase(record.title, query)
                || containsIgnoreCase(record.locationName, query)
                || containsIgnoreCase(record.transferFromPerson, query)
                || containsIgnoreCase(record.transferToPerson, query)
                || containsIgnoreCase(record.notesMarkdown, query)) {
            return true;
        }
        return false;
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void applySearchBarScroll(float dy) {
        if (searchBar == null) {
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

    private void confirmDelete(RecordEntity record) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete_record)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.deleteRecord(record.id, new Async.EmptyResult() {
                        @Override
                        public void onSuccess() {
                            reload();
                        }

                        @Override
                        public void onError(Throwable error) {
                            Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_record_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_chart) {
            Intent intent = new Intent(this, ChartActivity.class);
            intent.putExtra(ChartActivity.EXTRA_PROFILE_ID, profileId);
            startActivity(intent);
            return true;
        }
        if (id == R.id.action_share_card) {
            shareCard();
            return true;
        }
        if (id == R.id.action_transfer_qr) {
            showLanTransferQr();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareCard() {
        repository.getProfileDetails(profileId, new Async.Result<ProfileDetails>() {
            @Override
            public void onSuccess(ProfileDetails value) {
                repository.getRecords(profileId, new Async.Result<List<RecordEntity>>() {
                    @Override
                    public void onSuccess(List<RecordEntity> records) {
                        Collections.sort(records, (a, b) -> Long.compare(a.timestamp, b.timestamp));
                        List<String> recordIds = new ArrayList<>();
                        for (RecordEntity record : records) {
                            recordIds.add(record.id);
                        }
                        repository.getRecordFields(recordIds, new Async.Result<Map<String, List<RecordFieldEntity>>>() {
                            @Override
                            public void onSuccess(Map<String, List<RecordFieldEntity>> fieldsByRecord) {
                                repository.getRecordImages(recordIds, new Async.Result<Map<String, List<String>>>() {
                                    @Override
                                    public void onSuccess(Map<String, List<String>> imagesByRecord) {
                                        try {
                                            startActivity(CardShareManager.buildShareIntent(
                                                    RecordListActivity.this, value, records, fieldsByRecord, imagesByRecord));
                                        } catch (Throwable t) {
                                            Toast.makeText(RecordListActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable error) {
                                        Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }

                            @Override
                            public void onError(Throwable error) {
                                Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLanTransferQr() {
        repository.collectTransferBundle(profileId, new Async.Result<ExportBundle>() {
            @Override
            public void onSuccess(ExportBundle value) {
                startLanTransfer(value);
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startLanTransfer(ExportBundle bundle) {
        String localIp = NetworkUtil.getLocalIpAddress();
        if (localIp == null) {
            Toast.makeText(this, R.string.transfer_lan_no_ip, Toast.LENGTH_LONG).show();
            return;
        }
        stopActiveTransfer();
        activeTransferServer = new LanTransferServer(this, bundle);
        activeTransferServer.start(new LanTransferServer.Callback() {
            @Override
            public void onStarted(int port, String token) {
                LanTransferPayload payload = new LanTransferPayload();
                payload.type = "PETPROFILE_LAN_V1";
                payload.ip = localIp;
                payload.port = port;
                payload.token = token;
                Async.run(() -> {
                    try {
                        String qrText = QrCodeUtil.buildLanTransferQrText(payload);
                        Bitmap qr = QrCodeUtil.encode(qrText, 640);
                        Async.ui(() -> showLanQrDialog(qr));
                    } catch (Throwable t) {
                        Async.ui(() -> {
                            stopActiveTransfer();
                            Toast.makeText(RecordListActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                stopActiveTransfer();
                Toast.makeText(RecordListActivity.this, R.string.transfer_lan_failed, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onTransferCompleted(KeeperInfo receiverInfo) {
                repository.applyOutgoingTransfer(profileId, receiverInfo, new Async.EmptyResult() {
                    @Override
                    public void onSuccess() {
                        reload();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showLanQrDialog(Bitmap qr) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.action_show_qr);
        ImageView imageView = new ImageView(this);
        imageView.setPadding(24, 24, 24, 24);
        imageView.setImageBitmap(qr);
        builder.setView(imageView);
        builder.setMessage(R.string.transfer_lan_instructions);
        builder.setPositiveButton(R.string.action_cancel, (dialog, which) -> stopActiveTransfer());
        builder.setOnDismissListener(dialog -> stopActiveTransfer());
        builder.show();
    }

    private void stopActiveTransfer() {
        if (activeTransferServer != null) {
            activeTransferServer.stop();
            activeTransferServer = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopActiveTransfer();
        super.onDestroy();
    }
}
