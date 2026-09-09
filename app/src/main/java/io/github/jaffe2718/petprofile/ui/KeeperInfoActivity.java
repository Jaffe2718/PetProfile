package io.github.jaffe2718.petprofile.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.KeeperInfo;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.BackupManager;
import io.github.jaffe2718.petprofile.util.KeeperInfoManager;
import io.github.jaffe2718.petprofile.util.LocationHelper;
import io.github.jaffe2718.petprofile.util.OneDriveBackupManager;

public class KeeperInfoActivity extends AppCompatActivity {
    private static final int REQUEST_MAP = 5301;

    private EditText nicknameEditText;
    private Button homeButton;
    private TextView driveStatusText;
    private Button driveLoginButton;
    private ImageButton driveUploadButton;
    private ImageButton driveDownloadButton;
    private KeeperInfo draft = new KeeperInfo();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keeper_info);
        PetRepository repository = PetRepository.get(this);

        nicknameEditText = findViewById(R.id.keeperNicknameEditText);
        homeButton = findViewById(R.id.keeperHomeButton);
        driveStatusText = findViewById(R.id.driveStatusText);
        driveLoginButton = findViewById(R.id.driveLoginButton);
        driveUploadButton = findViewById(R.id.driveUploadButton);
        driveDownloadButton = findViewById(R.id.driveDownloadButton);

        draft = KeeperInfoManager.load(this);
        nicknameEditText.setText(draft.nickname);
        updateHomeButton();

        homeButton.setOnClickListener(v -> openHomeInMap());
        homeButton.setOnLongClickListener(v -> {
            pickHome();
            return true;
        });
        driveLoginButton.setOnClickListener(v -> toggleSignIn());
        driveUploadButton.setOnClickListener(v -> uploadToDrive());
        driveDownloadButton.setOnClickListener(v -> downloadFromDrive());
        findViewById(R.id.saveButton).setOnClickListener(v -> {
            draft.nickname = nicknameEditText.getText().toString().trim();
            KeeperInfoManager.save(this, draft);
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
            finish();
        });

        updateDriveUi();
    }

    private void updateHomeButton() {
        homeButton.setText(draft.hasHomePlace()
                ? draft.homePlace
                : getString(R.string.keeper_home_label));
    }

    private void pickHome() {
        double[] coords = LocationHelper.lastKnownCoordinates(this);
        double initialLatitude = coords == null ? 35.0 : coords[0];
        double initialLongitude = coords == null ? 105.0 : coords[1];
        LocationHelper.openMapPicker(this, REQUEST_MAP, initialLatitude, initialLongitude);
    }

    private void openHomeInMap() {
        if (draft.latitude == null || draft.longitude == null) {
            pickHome();
            return;
        }
        LocationHelper.openInMap(this, draft.latitude, draft.longitude);
    }

    private void toggleSignIn() {
        if (OneDriveBackupManager.isSignedIn(this)) {
            OneDriveBackupManager.signOut(this);
            Toast.makeText(this, R.string.onedrive_logout, Toast.LENGTH_SHORT).show();
            updateDriveUi();
            return;
        }
        OneDriveBackupManager.signIn(this, new OneDriveBackupManager.Callback() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(KeeperInfoActivity.this, R.string.onedrive_connected, Toast.LENGTH_SHORT).show();
                updateDriveUi();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(KeeperInfoActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateDriveUi() {
        boolean signedIn = OneDriveBackupManager.isSignedIn(this);
        String account = OneDriveBackupManager.getAccountName(this);
        if (signedIn) {
            driveStatusText.setText(account != null
                    ? getString(R.string.onedrive_connected_as, account)
                    : getString(R.string.onedrive_connected));
            driveLoginButton.setText(R.string.onedrive_logout);
        } else {
            driveStatusText.setText(R.string.onedrive_login_hint);
            driveLoginButton.setText(R.string.onedrive_login);
        }
        driveUploadButton.setEnabled(signedIn && !OneDriveBackupManager.isCloudBusy());
        driveDownloadButton.setEnabled(signedIn && !OneDriveBackupManager.isCloudBusy());
    }

    private void uploadToDrive() {
        if (OneDriveBackupManager.isCloudBusy()) {
            Toast.makeText(this, R.string.onedrive_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        OneDriveBackupManager.setCloudBusy(true);
        updateDriveUi();
        PetRepository.get(this).exportAll(new Async.Result<ExportBundle>() {
            @Override
            public void onSuccess(ExportBundle bundle) {
                Async.run(() -> {
                    try {
                        byte[] zipBytes = BackupManager.createZipBytes(KeeperInfoActivity.this, bundle);
                        OneDriveBackupManager.upload(KeeperInfoActivity.this, zipBytes, new OneDriveBackupManager.Callback() {
                            @Override
                            public void onSuccess(String message) {
                                OneDriveBackupManager.setCloudBusy(false);
                                Toast.makeText(KeeperInfoActivity.this, message, Toast.LENGTH_SHORT).show();
                                updateDriveUi();
                            }

                            @Override
                            public void onError(String message) {
                                OneDriveBackupManager.setCloudBusy(false);
                                Toast.makeText(KeeperInfoActivity.this, message, Toast.LENGTH_LONG).show();
                                updateDriveUi();
                            }
                        });
                    } catch (Throwable t) {
                        OneDriveBackupManager.setCloudBusy(false);
                        Async.ui(() -> Toast.makeText(KeeperInfoActivity.this, t.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                OneDriveBackupManager.setCloudBusy(false);
                Toast.makeText(KeeperInfoActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void downloadFromDrive() {
        if (OneDriveBackupManager.isCloudBusy()) {
            Toast.makeText(this, R.string.onedrive_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        OneDriveBackupManager.setCloudBusy(true);
        updateDriveUi();
        OneDriveBackupManager.download(this, new OneDriveBackupManager.Callback() {
            @Override
            public void onSuccess(String message) {
                OneDriveBackupManager.setCloudBusy(false);
                Toast.makeText(KeeperInfoActivity.this, message, Toast.LENGTH_SHORT).show();
                updateDriveUi();
            }

            @Override
            public void onError(String message) {
                OneDriveBackupManager.setCloudBusy(false);
                Toast.makeText(KeeperInfoActivity.this, message, Toast.LENGTH_LONG).show();
                updateDriveUi();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MAP) {
            if (resultCode != RESULT_OK || data == null) {
                return;
            }
            double pickedLatitude = data.getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LATITUDE, 0.0);
            double pickedLongitude = data.getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LONGITUDE, 0.0);
            draft.latitude = pickedLatitude;
            draft.longitude = pickedLongitude;
            LocationHelper.resolveAddress(this, pickedLatitude, pickedLongitude, new LocationHelper.Callback() {
                @Override
                public void onResult(LocationHelper.LocationResult result) {
                    draft.homePlace = result.name + " "
                            + LocationHelper.formatDms(result.latitude, true)
                            + ", " + LocationHelper.formatDms(result.longitude, false);
                    updateHomeButton();
                }

                @Override
                public void onError(String message) {
                    draft.homePlace = LocationHelper.formatDms(pickedLatitude, true)
                            + ", " + LocationHelper.formatDms(pickedLongitude, false);
                    updateHomeButton();
                }
            });
        }
    }
}
