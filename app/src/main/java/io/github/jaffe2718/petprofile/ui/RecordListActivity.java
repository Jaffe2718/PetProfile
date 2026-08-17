package io.github.jaffe2718.petprofile.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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

public class RecordListActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_ID = "profile_id";

    private PetRepository repository;
    private String profileId;
    private RecordAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private LanTransferServer activeTransferServer;

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
                List<String> recordIds = new ArrayList<>();
                for (RecordEntity record : value) {
                    recordIds.add(record.id);
                }
                repository.getRecordImages(recordIds, new Async.Result<Map<String, List<String>>>() {
                    @Override
                    public void onSuccess(Map<String, List<String>> imageMap) {
                        adapter.setItems(value, imageMap);
                        emptyTextView.setVisibility(value.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                    }

                    @Override
                    public void onError(Throwable error) {
                        adapter.setItems(value, null);
                        emptyTextView.setVisibility(value.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(RecordListActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
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
