package io.github.jaffe2718.petprofile.ui;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

import io.github.jaffe2718.petprofile.util.OneDriveBackupManager;

/**
 * Receives the OneDrive OAuth redirect (msal{client_id}://auth?code=...) and
 * hands it to OneDriveBackupManager, which exchanges the code for a token.
 */
public class OneDriveAuthActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        if (uri != null) {
            OneDriveBackupManager.handleRedirect(this, uri);
        }
        finish();
    }
}
