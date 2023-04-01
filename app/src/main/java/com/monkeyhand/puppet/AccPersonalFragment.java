package com.monkeyhand.puppet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import com.monkeyhand.puppet.media.VxCard;
import com.monkeyhand.puppet.model.Credential;
import com.monkeyhand.puppet.model.MsgSetMeta;
import com.monkeyhand.puppet.model.ServerMessage;

/**
 * Fragment for editing current user details.
 */
public class AccPersonalFragment extends Fragment
        implements ChatsActivity.FormUpdatable, UiUtils.AvatarPreviewer, MenuProvider {

    private final ActivityResultLauncher<Intent> mAvatarPickerLauncher =
            UiUtils.avatarPickerLauncher(this, this);

    private final ActivityResultLauncher<String[]> mRequestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String, Boolean> e : result.entrySet()) {
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
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();
        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_acc_personal, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.general);
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((MenuHost) requireActivity()).addMenuProvider(this,
                getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        final FragmentActivity activity = getActivity();
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();

        if (me == null || activity == null) {
            return;
        }

        // Attach listeners to editable form fields.

        activity.findViewById(R.id.uploadAvatar).setOnClickListener(v -> {
            Intent launcher = UiUtils.avatarSelectorIntent(activity, mRequestPermissionsLauncher);
            if (launcher != null) {
                mAvatarPickerLauncher.launch(launcher);
            }
        });

        activity.findViewById(R.id.buttonManageTags).setOnClickListener(view -> showEditTags());

        activity.findViewById(R.id.buttonAddContact).setOnClickListener(view -> showAddCredential());

        // Assign initial form values.
        updateFormValues(activity, me);

        super.onResume();
    }

    @Override
    public void updateFormValues(@NonNull final FragmentActivity activity, final MeTopic<VxCard> me) {
        String fn = null;
        String description = null;
        if (me != null) {
            LayoutInflater inflater = LayoutInflater.from(activity);

            LinearLayout credList = activity.findViewById(R.id.credList);

            // Remove all items from the list of credentials.
            while (credList.getChildCount() > 0) {
                credList.removeViewAt(0);
            }

            Credential[] creds = me.getCreds();
            if (creds != null) {
                for (Credential cred : creds) {
                    View container = inflater.inflate(R.layout.credential, credList, false);
                    ((TextView) container.findViewById(R.id.method)).setText(cred.meth);
                    ((TextView) container.findViewById(R.id.value)).setText(cred.val);
                    Button btn = container.findViewById(R.id.buttonConfirm);
                    if (cred.isDone()) {
                        btn.setVisibility(View.GONE);
                    } else {
                        btn.setVisibility(View.VISIBLE);
                        btn.setTag(cred);
                    }
                    btn.setOnClickListener(view -> {
                        Credential cred1 = (Credential) view.getTag();
                        showConfirmCredential(cred1.meth, cred1.val);
                    });

                    ImageButton ibtn = container.findViewById(R.id.buttonDelete);
                    ibtn.setTag(cred);
                    ibtn.setOnClickListener(view -> {
                        Credential cred12 = (Credential) view.getTag();
                        showDeleteCredential(cred12.meth, cred12.val);
                    });
                    credList.addView(container, 0);
                }
            }
            credList.requestLayout();

            VxCard pub = me.getPub();
            UiUtils.setAvatar(activity.findViewById(R.id.imageAvatar), pub, Cache.getTinode().getMyId(), false);
            if (pub != null) {
                fn = pub.fn;
                description = pub.note;
            }

            FlexboxLayout tagsView = activity.findViewById(R.id.tagList);
            tagsView.removeAllViews();

            String[] tags = me.getTags();
            if (tags != null) {
                for (String tag : tags) {
                    TextView label = (TextView) inflater.inflate(R.layout.tag, tagsView, false);
                    label.setText(tag);
                    tagsView.addView(label);
                    label.requestLayout();
                }
            }
            tagsView.requestLayout();
        }

        ((TextView) activity.findViewById(R.id.topicTitle)).setText(fn);
        ((TextView) activity.findViewById(R.id.topicDescription)).setText(description);
    }

    // Dialog for editing tags.
    private void showEditTags() {
        final Activity activity = requireActivity();

        final MeTopic me = Cache.getTinode().getMeTopic();
        String[] tagArray = me.getTags();
        String tags = tagArray != null ? TextUtils.join(", ", tagArray) : "";

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_edit_tags, null);
        builder.setView(editor).setTitle(R.string.tags_management);

        final EditText tagsEditor = editor.findViewById(R.id.editTags);
        tagsEditor.setText(tags);
        builder
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String[] tags1 = UiUtils.parseTags(tagsEditor.getText().toString());
                    // noinspection unchecked
                    me.setMeta(new MsgSetMeta.Builder().with(tags1).build())
                            .thenCatch(new UiUtils.ToastFailureListener(activity));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Dialog for confirming a credential.
    private void showConfirmCredential(final String meth, final String val) {
        final Activity activity = requireActivity();

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_validate, null);
        builder.setView(editor).setTitle(R.string.validate_cred_title)
                .setMessage(getString(R.string.validate_cred, meth))
                // FIXME: check for empty input and refuse to dismiss the dialog.
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String response = ((EditText) editor.findViewById(R.id.response)).getText().toString();
                    if (TextUtils.isEmpty(response)) {
                        return;
                    }

                    final MeTopic me = Cache.getTinode().getMeTopic();
                    //noinspection unchecked
                    me.confirmCred(meth, response).thenCatch(new UiUtils.ToastFailureListener(activity));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Show dialog for deleting credential
    private void showDeleteCredential(final String meth, final String val) {
        final Activity activity = requireActivity();

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setNegativeButton(android.R.string.cancel, null)
                .setTitle(R.string.delete_credential_title)
                .setMessage(getString(R.string.delete_credential_confirmation, meth, val))
                .setCancelable(true)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    final MeTopic me = Cache.getTinode().getMeTopic();
                    // noinspection unchecked
                    me.delCredential(meth, val)
                            .thenCatch(new UiUtils.ToastFailureListener(activity));
                })
                .show();
    }

    private void showAddCredential() {
        final Activity activity = requireActivity();

        final View editor = LayoutInflater.from(activity).inflate(R.layout.dialog_add_credential, null);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(editor).setTitle(R.string.add_credential_title)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                    EditText credEditor = editor.findViewById(R.id.editCredential);
                    String cred = credEditor.getText().toString().trim().toLowerCase();
                    if (TextUtils.isEmpty(cred)) {
                        return;
                    }
                    Credential parsed = UiUtils.parseCredential(cred);
                    if (parsed != null) {
                        final MeTopic me = Cache.getTinode().getMeTopic();
                        // noinspection unchecked
                        me.setMeta(new MsgSetMeta.Builder().with(parsed).build())
                                .thenCatch(new UiUtils.ToastFailureListener(activity));

                        // Dismiss once everything is OK.
                        dialog.dismiss();
                    } else {
                        credEditor.setError(activity.getString(R.string.unrecognized_credential));
                    }
                }));

        dialog.show();
    }

    @Override
    public void showAvatarPreview(final Bundle args) {
        final Activity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_AVATAR_PREVIEW, args);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_save, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            FragmentActivity activity = requireActivity();
            if (activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }
            final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
            String title = ((TextView) activity.findViewById(R.id.topicTitle)).getText().toString().trim();
            String description = ((TextView) activity.findViewById(R.id.topicDescription)).getText().toString().trim();
            UiUtils.updateTopicDesc(me, title, null, description)
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage unused) {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                activity.runOnUiThread(() -> activity.getSupportFragmentManager().popBackStack());
                            }
                            return null;
                        }
                    })
                    .thenCatch(new UiUtils.ToastFailureListener(activity));
            return true;
        }
        return false;
    }
}
