package com.monkeyhand.puppet;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.monkeyhand.puppet.account.Utils;
import com.monkeyhand.puppet.utils.Base64Encoder;
import com.monkeyhand.puppet.model.AuthScheme;
import com.monkeyhand.puppet.model.ServerMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "LoginFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        setHasOptionsMenu(true);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return null;
        }

        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(false);
            bar.setTitle(R.string.app_name);
        }

        View fragment = inflater.inflate(R.layout.fragment_login, container, false);

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
        String login = pref.getString(LoginActivity.PREFS_LAST_LOGIN, null);

        if (!TextUtils.isEmpty(login)) {
            TextView loginView = fragment.findViewById(R.id.editLogin);
            if (loginView != null) {
                loginView.setText(login);
            }
        }

        fragment.findViewById(R.id.signIn).setOnClickListener(this);
        fragment.findViewById(R.id.forgotPassword).setOnClickListener(this);

        return fragment;
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_login, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Either [Signin] or [Forgot password] pressed.
     *
     * @param v ignored
     */
    public void onClick(View v) {
        final LoginActivity parent = (LoginActivity) getActivity();
        if (parent == null) {
            return;
        }

        if (v.getId() == R.id.forgotPassword) {
            parent.showFragment(LoginActivity.FRAGMENT_RESET, null);
            return;
        }

        EditText loginInput = parent.findViewById(R.id.editLogin);
        EditText passwordInput = parent.findViewById(R.id.editPassword);

        final String inviteCode = loginInput.getText().toString().trim();
        if (inviteCode.isEmpty()) {
            loginInput.setError(getText(R.string.login_required));
            return;
        }
        String _decoded = Base64Encoder.Decode(inviteCode);
        String[] _split = _decoded.split("/");
        if (_split.length != 2) {
            loginInput.setError(getText(R.string.invalid_login));
            return;
        }
        final String login = _split[0];
        final String password = _split[1];

        // 目前只接受邀请码登录
//        final String password = passwordInput.getText().toString().trim();
//        if (password.isEmpty()) {
//            passwordInput.setError(getText(R.string.password_required));
//            return;
//        }

        final Button signIn = parent.findViewById(R.id.signIn);
        signIn.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        final String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName(parent));
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());
        final Tinode tinode = Cache.getTinode();
        // This is called on the websocket thread.
        tinode.connect(hostName, tls, false)
                .thenApply(
                        new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) {
                                return tinode.loginBasic(
                                        login,
                                        password);
                            }
                        })
                .thenApply(
                        new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) {
                                sharedPref.edit().putString(LoginActivity.PREFS_LAST_LOGIN, inviteCode).apply();

                                UiUtils.updateAndroidAccount(parent, tinode.getMyId(),
                                        AuthScheme.basicInstance(login, password).toString(),
                                        tinode.getAuthToken(), tinode.getAuthTokenExpiration());

                                // msg could be null if earlier login has succeeded.
                                if (msg != null && msg.ctrl.code >= 300 &&
                                        msg.ctrl.text.contains("validate credentials")) {
                                    parent.runOnUiThread(() -> {
                                        signIn.setEnabled(true);
                                        parent.showFragment(LoginActivity.FRAGMENT_CREDENTIALS, null);
                                    });
                                } else {
                                    tinode.setAutoLoginToken(tinode.getAuthToken());
                                    // Force immediate sync, otherwise Contacts tab may be unusable.
                                    UiUtils.onLoginSuccess(parent, signIn, tinode.getMyId());
                                }
                                return null;
                            }
                        })
                .thenCatch(
                        new PromisedReply.FailureListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onFailure(Exception err) {
                                Log.i(TAG, "Login failed", err);
                                parent.reportError(err, signIn, 0, R.string.error_login_failed);
                                return null;
                            }
                        });
    }
}
