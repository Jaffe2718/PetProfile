package io.github.jaffe2718.petprofile.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.mcp.McpServer;
import io.github.jaffe2718.petprofile.mcp.McpService;
import io.github.jaffe2718.petprofile.mcp.McpTokenManager;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.BackupManager;
import io.github.jaffe2718.petprofile.util.OneDriveBackupManager;
import io.github.jaffe2718.petprofile.util.RoutineNotifier;
import io.github.jaffe2718.petprofile.util.RoutineScheduler;
import io.github.jaffe2718.petprofile.util.UpdateManager;

import java.io.File;

public class MiscToolsActivity extends AppCompatActivity {
    private static final int REQUEST_EXPORT = 5501;
    private static final int REQUEST_IMPORT = 5502;
    private ExportBundle pendingExportBundle;
    private Dialog mcpDialog;
    private UpdateManager.Download updateDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_misc_tools);

        findViewById(R.id.todoButton).setOnClickListener(v ->
                startActivity(new Intent(this, DailyTodoActivity.class)));
        findViewById(R.id.keeperButton).setOnClickListener(v ->
                startActivity(new Intent(this, KeeperInfoActivity.class)));
        findViewById(R.id.exportButton).setOnClickListener(v -> exportZip());
        findViewById(R.id.importButton).setOnClickListener(v -> requestImport());
        findViewById(R.id.syncButton).setOnClickListener(v -> syncDrive());
        findViewById(R.id.syncDownButton).setOnClickListener(v -> syncFromCloud());
        findViewById(R.id.scanButton).setOnClickListener(v ->
                startActivity(new Intent(this, QrScannerActivity.class)));
        findViewById(R.id.languageButton).setOnClickListener(v -> chooseLanguage());
        findViewById(R.id.aboutButton).setOnClickListener(v -> showAbout());
        findViewById(R.id.updateButton).setOnClickListener(v -> checkUpdate());
        updateCloudButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCloudButtons();
    }

    private void updateCloudButtons() {
        boolean enabled = OneDriveBackupManager.isSignedIn(this) && !OneDriveBackupManager.isCloudBusy();
        setCellEnabled(findViewById(R.id.syncButton), enabled);
        setCellEnabled(findViewById(R.id.syncDownButton), enabled);
    }

    private void setCellEnabled(View cell, boolean enabled) {
        if (cell == null) {
            return;
        }
        cell.setEnabled(enabled);
        int color = enabled ? getColor(R.color.text_primary) : getColor(R.color.text_disabled);
        if (cell instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) cell).getChildCount(); i++) {
                View child = ((ViewGroup) cell).getChildAt(i);
                if (child instanceof ImageView) {
                    ((ImageView) child).setImageTintList(ColorStateList.valueOf(color));
                } else if (child instanceof TextView) {
                    ((TextView) child).setTextColor(color);
                }
            }
        }
    }

    private void requestImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    private void exportZip() {
        PetRepository.get(this).exportAll(new Async.Result<ExportBundle>() {
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
                Toast.makeText(MiscToolsActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void syncDrive() {
        if (!OneDriveBackupManager.hasClientId(this)) {
            Toast.makeText(this, R.string.onedrive_no_client_id, Toast.LENGTH_LONG).show();
            return;
        }
        if (OneDriveBackupManager.isCloudBusy()) {
            Toast.makeText(this, R.string.onedrive_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        OneDriveBackupManager.setCloudBusy(true);
        updateCloudButtons();
        PetRepository.get(this).exportAll(new Async.Result<ExportBundle>() {
            @Override
            public void onSuccess(ExportBundle bundle) {
                OneDriveBackupManager.upload(MiscToolsActivity.this, bundle, new OneDriveBackupManager.Callback() {
                    @Override
                    public void onSuccess(String message) {
                        OneDriveBackupManager.setCloudBusy(false);
                        updateCloudButtons();
                        Toast.makeText(MiscToolsActivity.this, message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        OneDriveBackupManager.setCloudBusy(false);
                        updateCloudButtons();
                        Toast.makeText(MiscToolsActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                OneDriveBackupManager.setCloudBusy(false);
                updateCloudButtons();
                Toast.makeText(MiscToolsActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void syncFromCloud() {
        if (!OneDriveBackupManager.hasClientId(this)) {
            Toast.makeText(this, R.string.onedrive_no_client_id, Toast.LENGTH_LONG).show();
            return;
        }
        if (OneDriveBackupManager.isCloudBusy()) {
            Toast.makeText(this, R.string.onedrive_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        // The restore fills in what is missing and is resumable, so it starts right away.
        OneDriveBackupManager.setCloudBusy(true);
        updateCloudButtons();
        OneDriveBackupManager.download(this, new OneDriveBackupManager.Callback() {
            @Override
            public void onSuccess(String message) {
                OneDriveBackupManager.setCloudBusy(false);
                updateCloudButtons();
                Toast.makeText(MiscToolsActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                OneDriveBackupManager.setCloudBusy(false);
                updateCloudButtons();
                Toast.makeText(MiscToolsActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

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

    /**
     * Checks the latest GitHub release, then downloads, verifies and installs it inside the app.
     * Nothing here opens a browser any more.
     */
    private void checkUpdate() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show();
        UpdateManager.fetchLatest(this, new Async.Result<UpdateManager.Release>() {
            @Override
            public void onSuccess(UpdateManager.Release release) {
                if (release == null) {
                    toast(getString(R.string.update_latest));
                    return;
                }
                showUpdateDialog(release);
            }

            @Override
            public void onError(Throwable error) {
                toast(getString(R.string.update_error));
            }
        });
    }

    private void showUpdateDialog(UpdateManager.Release release) {
        StringBuilder message = new StringBuilder(getString(R.string.update_available_msg, release.tag));
        if (release.size > 0) {
            message.append('\n').append(getString(R.string.update_download_size,
                    UpdateManager.formatBytes(release.size)));
        }
        // A verified package may already be sitting in the cache, for instance when the download
        // finished but the install did not happen; then this is an install, not a download.
        boolean cached = release.cachedApk != null;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_available)
                .setMessage(message)
                .setPositiveButton(cached ? R.string.update_install : R.string.update_download,
                        (d, w) -> {
                            if (cached) {
                                installUpdate(release.cachedApk);
                            } else {
                                startUpdateDownload(release);
                            }
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void installUpdate(File apk) {
        if (!UpdateManager.install(this, apk)) {
            toast(getString(R.string.update_install_permission));
        }
    }

    private void startUpdateDownload(UpdateManager.Release release) {
        int padding = dp(24);
        TextView status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(10));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(padding, dp(4), padding, 0);
        box.addView(status);
        box.addView(bar);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_downloading)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton(R.string.action_cancel, (d, w) -> {
                    if (updateDownload != null) {
                        updateDownload.cancel();
                    }
                })
                .create();
        dialog.show();

        updateDownload = UpdateManager.download(this, release, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean resumed) {
                Async.ui(() -> {
                    int percent = total > 0 ? (int) Math.min(100, downloaded * 100 / total) : 0;
                    bar.setProgress(total > 0 ? (int) (downloaded * 1000 / total) : 0);
                    status.setText(getString(R.string.update_download_progress, percent,
                            UpdateManager.formatBytes(downloaded), UpdateManager.formatBytes(total)));
                });
            }

            @Override
            public void onVerifying() {
                Async.ui(() -> status.setText(R.string.update_verifying));
            }

            @Override
            public void onReady(File apk) {
                Async.ui(() -> {
                    dialog.dismiss();
                    installUpdate(apk);
                });
            }

            @Override
            public void onError(String message) {
                Async.ui(() -> {
                    dialog.dismiss();
                    if (message == null || message.isEmpty()) {
                        toast(getString(R.string.update_download_cancelled));
                    } else {
                        toast(getString(R.string.update_download_failed, message));
                    }
                });
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private android.view.View buildAboutTitle() {
        android.view.View titleView = getLayoutInflater().inflate(R.layout.view_about_title, null);
        ImageButton mcpButton = titleView.findViewById(R.id.aboutMcpButton);
        mcpButton.setOnClickListener(v -> showMcpDialog());
        return titleView;
    }

    private void showMcpDialog() {
        if (mcpDialog != null && mcpDialog.isShowing()) {
            return;
        }
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_mcp, null);
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
            getSharedPreferences("pet_profile_mcp", MODE_PRIVATE).edit().putBoolean("mcp_enabled", isChecked).apply();
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
        copyButton.setOnClickListener(v -> copyText(keyText.getText().toString()));
        urlCopyButton.setOnClickListener(v -> copyText(urlText.getText().toString()));

        mcpDialog = dialog;
        dialog.show();
    }

    private void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("PetProfile MCP", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.mcp_copied, Toast.LENGTH_SHORT).show();
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
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQUEST_EXPORT && pendingExportBundle != null) {
            final ExportBundle bundle = pendingExportBundle;
            pendingExportBundle = null;
            Async.run(() -> {
                try {
                    BackupManager.exportZip(this, bundle, uri);
                    Async.ui(() -> Toast.makeText(this, R.string.exported, Toast.LENGTH_SHORT).show());
                } catch (Throwable t) {
                    Async.ui(() -> Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        } else if (requestCode == REQUEST_IMPORT) {
            Async.run(() -> {
                try {
                    ExportBundle bundle = BackupManager.readZip(this, uri);
                    PetRepository.get(this).importBundle(bundle, new Async.EmptyResult() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(MiscToolsActivity.this, R.string.imported, Toast.LENGTH_SHORT).show();
                            RoutineScheduler.scheduleAll(MiscToolsActivity.this);
                            RoutineNotifier.sync(MiscToolsActivity.this);
                        }

                        @Override
                        public void onError(Throwable error) {
                            Toast.makeText(MiscToolsActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Throwable t) {
                    Async.ui(() -> Toast.makeText(MiscToolsActivity.this, t.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        }
    }
}
