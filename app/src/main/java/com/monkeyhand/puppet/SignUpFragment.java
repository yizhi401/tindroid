package com.monkeyhand.puppet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import com.monkeyhand.puppet.account.Utils;
import com.monkeyhand.puppet.media.VxCard;
import com.monkeyhand.puppet.model.AuthScheme;
import com.monkeyhand.puppet.model.Credential;
import com.monkeyhand.puppet.model.MetaSetDesc;
import com.monkeyhand.puppet.model.ServerMessage;

/**
 * Fragment for managing registration of a new account.
 */
public class SignUpFragment extends Fragment
        implements View.OnClickListener, UiUtils.AvatarPreviewer {

    private static final String TAG ="SignUpFragment";

    private final ActivityResultLauncher<Intent> mAvatarPickerLauncher =
            UiUtils.avatarPickerLauncher(this, this);

    private final ActivityResultLauncher<String[]> mRequestAvatarPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    // Check if all required permissions are granted.
                    if (!e.getValue()) {
                        return;
                    }
                }
                FragmentActivity activity = requireActivity();
                // Try to open the image selector again.
                Intent launcher = UiUtils.avatarSelectorIntent(activity, null);
                if (launcher != null) {
                    mAvatarPickerLauncher.launch(launcher);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        AppCompatActivity activity = (AppCompatActivity) requireActivity();

        ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.sign_up);
        }

        View fragment = inflater.inflate(R.layout.fragment_signup, container, false);

        // Get avatar from the gallery or photo camera.
        fragment.findViewById(R.id.uploadAvatar).setOnClickListener(v -> {
            Intent launcher = UiUtils.avatarSelectorIntent(activity, mRequestAvatarPermissionsLauncher);
            if (launcher != null) {
                mAvatarPickerLauncher.launch(launcher);
            }
        });
        // Handle click on the sign up button.
        fragment.findViewById(R.id.signUp).setOnClickListener(this);

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final LoginActivity parent = (LoginActivity) requireActivity();
        if (parent.isFinishing() || parent.isDestroyed()) {
            return;
        }

        AvatarViewModel avatarVM = new ViewModelProvider(parent).get(AvatarViewModel.class);
        avatarVM.getAvatar().observe(getViewLifecycleOwner(), bmp ->
            UiUtils.acceptAvatar(parent, parent.findViewById(R.id.imageAvatar), bmp)
        );
    }

    /**
     * Create new account.
     */
    @Override
    public void onClick(View v) {
        final LoginActivity parent = (LoginActivity) requireActivity();
        if (parent.isFinishing() || parent.isDestroyed()) {
            return;
        }

        final String login = ((EditText) parent.findViewById(R.id.newLogin)).getText().toString().trim();
        if (login.isEmpty()) {
            ((EditText) parent.findViewById(R.id.newLogin)).setError(getText(R.string.login_required));
            return;
        }
        if (login.contains(":")) {
            ((EditText) parent.findViewById(R.id.newLogin)).setError(getText(R.string.invalid_login));
            return;
        }

        final String password = ((EditText) parent.findViewById(R.id.newPassword)).getText().toString().trim();
        if (password.isEmpty()) {
            ((EditText) parent.findViewById(R.id.newPassword)).setError(getText(R.string.password_required));
            return;
        }

        final String email = ((EditText) parent.findViewById(R.id.email)).getText().toString().trim();
        if (email.isEmpty()) {
            ((EditText) parent.findViewById(R.id.email)).setError(getText(R.string.email_required));
            return;
        }

        String fn = ((EditText) parent.findViewById(R.id.fullName)).getText().toString().trim();
        if (fn.isEmpty()) {
            ((EditText) parent.findViewById(R.id.fullName)).setError(getText(R.string.full_name_required));
            return;
        }
        // Make sure user name is not too long.
        final String fullName;
        if (fn.length() > Const.MAX_TITLE_LENGTH) {
            fullName = fn.substring(0, Const.MAX_TITLE_LENGTH);
        } else {
            fullName = fn;
        }

        String description = ((EditText) parent.findViewById(R.id.userDescription)).getText().toString().trim();
        if (!TextUtils.isEmpty(description)) {
            if (description.length() > Const.MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, Const.MAX_DESCRIPTION_LENGTH);
            }
        } else {
            description = null;
        }

        final Button signUp = parent.findViewById(R.id.signUp);
        signUp.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName(parent));
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final ImageView avatar = parent.findViewById(R.id.imageAvatar);
        final Tinode tinode = Cache.getTinode();
        final VxCard theCard = new VxCard(fullName, description);
        Drawable dr = avatar.getDrawable();
        final Bitmap bmp;
        if (dr instanceof BitmapDrawable) {
            bmp = ((BitmapDrawable) dr).getBitmap();
        } else {
            bmp = null;
        }
        // This is called on the websocket thread.
        tinode.connect(hostName, tls, false)
                .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) {
                                return AttachmentHandler.uploadAvatar(theCard, bmp, "newacc");
                            }
                        })
                .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) {
                                // Try to create a new account.
                                MetaSetDesc<VxCard, String> meta = new MetaSetDesc<>(theCard, null);
                                meta.attachments = theCard.getPhotoRefs();
                                return tinode.createAccountBasic(
                                        login, password, true, null, meta,
                                        Credential.append(null, new Credential("email", email)));
                            }
                        })
                .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) {
                                UiUtils.updateAndroidAccount(parent, tinode.getMyId(),
                                        AuthScheme.basicInstance(login, password).toString(),
                                        tinode.getAuthToken(), tinode.getAuthTokenExpiration());

                                // Remove used avatar from the view model.
                                new ViewModelProvider(parent).get(AvatarViewModel.class).clear();

                                // Flip back to login screen on success;
                                parent.runOnUiThread(() -> {
                                    if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                        signUp.setEnabled(true);
                                        parent.showFragment(LoginActivity.FRAGMENT_CREDENTIALS, null);
                                    } else {
                                        // We are requesting immediate login with the new account.
                                        // If the action succeeded, assume we have logged in.
                                        tinode.setAutoLoginToken(tinode.getAuthToken());
                                        UiUtils.onLoginSuccess(parent, signUp, tinode.getMyId());
                                    }
                                });
                                return null;
                            }
                        })
                .thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onFailure(Exception err) {
                                if (!SignUpFragment.this.isVisible()) {
                                    return null;
                                }
                                parent.runOnUiThread(() -> {
                                    signUp.setEnabled(true);
                                    if (err instanceof ServerResponseException) {
                                        final String cause = ((ServerResponseException) err).getReason();
                                        if (cause != null) {
                                            switch (cause) {
                                                case "auth":
                                                    // Invalid login
                                                    ((EditText) parent.findViewById(R.id.newLogin))
                                                            .setError(getText(R.string.login_rejected));
                                                    break;
                                                case "email":
                                                    // Duplicate email:
                                                    ((EditText) parent.findViewById(R.id.email))
                                                            .setError(getText(R.string.email_rejected));
                                                    break;
                                            }
                                        }
                                    } else {
                                        Log.w(TAG, "Failed create account", err);
                                        Toast.makeText(parent, parent.getString(R.string.action_failed),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                                parent.reportError(err, signUp, 0, R.string.error_new_account_failed);
                                return null;
                            }
                        });
    }

    @Override
    public void showAvatarPreview(Bundle args) {
        final FragmentActivity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        ((LoginActivity) activity).showFragment(LoginActivity.FRAGMENT_AVATAR_PREVIEW, args);
    }
}
