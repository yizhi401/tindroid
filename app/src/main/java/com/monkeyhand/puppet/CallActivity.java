package com.monkeyhand.puppet;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.monkeyhand.puppet.media.VxCard;
import com.monkeyhand.puppet.model.ServerMessage;

public class CallActivity extends AppCompatActivity  {
    private static final String TAG = "CallActivity";

    static final String FRAGMENT_ACTIVE = "active_call";
    static final String FRAGMENT_INCOMING = "incoming_call";

    public static final String INTENT_ACTION_CALL_INCOMING = "puppet.intent.action.call.INCOMING";
    public static final String INTENT_ACTION_CALL_START = "puppet.intent.action.call.START";

    private boolean mTurnScreenOffWhenDone;

    private Tinode mTinode;

    private String mTopicName;
    private int mSeq;
    private ComTopic<VxCard> mTopic;
    private EventListener mLoginListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.cancel(CallManager.NOTIFICATION_TAG_INCOMING_CALL, 0);

        final Intent intent = getIntent();
        final String action = intent != null ? intent.getAction() : null;
        if (action == null) {
            Log.w(TAG, "No intent or no valid action, unable to proceed");
            finish();
            return;
        }

        // Using action once.
        intent.setAction(null);

        mTinode = Cache.getTinode();

        mTopicName = intent.getStringExtra(Const.INTENT_EXTRA_TOPIC);
        mSeq = intent.getIntExtra(Const.INTENT_EXTRA_SEQ, -1);
        // noinspection unchecked
        mTopic = (ComTopic<VxCard>) mTinode.getTopic(mTopicName);
        if (mTopic == null) {
            Log.e(TAG, "Invalid topic '" + mTopicName + "'");
            finish();
            return;
        }

        Cache.setSelectedTopicName(mTopicName);
        mLoginListener = new EventListener();
        mTinode.addListener(mLoginListener);

        Bundle args = new Bundle();
        args.putBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY,
                intent.getBooleanExtra(Const.INTENT_EXTRA_CALL_AUDIO_ONLY, false));
        String fragmentToShow;
        switch (action) {
            case INTENT_ACTION_CALL_INCOMING:
                // Incoming call started by the ser
                boolean accepted = intent.getBooleanExtra(Const.INTENT_EXTRA_CALL_ACCEPTED, false);
                fragmentToShow = accepted ? FRAGMENT_ACTIVE : FRAGMENT_INCOMING;
                args.putString(Const.INTENT_EXTRA_CALL_DIRECTION, "incoming");
                break;

            case INTENT_ACTION_CALL_START:
                // Call started by the current user.
                args.putString(Const.INTENT_EXTRA_CALL_DIRECTION, "outgoing");
                fragmentToShow = FRAGMENT_ACTIVE;
                break;

            default:
                Log.e(TAG, "Unknown call action '" + action + "'");
                finish();
                return;
        }
        setContentView(R.layout.activity_call);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        // Turn screen on and unlock.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //KeyguardManager mgr = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            //mgr.requestDismissKeyguard(this, null);
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mTurnScreenOffWhenDone = !pm.isInteractive();
        // Try to reconnect and subscribe.
        topicAttach();
        showFragment(fragmentToShow, args);
    }

    @Override
    public void onDestroy() {
        if (mTinode != null) {
            mTinode.removeListener(mLoginListener);
        }
        Cache.endCallInProgress();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        if (mTurnScreenOffWhenDone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false);
            setTurnScreenOn(false);
        }

        super.onDestroy();
    }

    void acceptCall() {
        Bundle args = new Bundle();
        args.putString(Const.INTENT_EXTRA_CALL_DIRECTION, "incoming");
        showFragment(FRAGMENT_ACTIVE, args);
    }

    void declineCall() {
        Cache.endCallInProgress();
        // Send message to server that the call is declined.
        if (mTopic != null) {
            mTopic.videoCallHangUp(mSeq);
        }
        finish();
    }

    void finishCall() {
        finish();
    }

    void showFragment(String tag, Bundle args) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();

        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_INCOMING:
                    fragment = new IncomingCallFragment();
                    break;
                case FRAGMENT_ACTIVE:
                    fragment = new CallFragment();
                    break;
                default:
                    throw new IllegalArgumentException("Failed to create fragment: unknown tag " + tag);
            }
        } else if (args == null) {
            // Retain old arguments.
            args = fragment.getArguments();
        }

        args = args != null ? args : new Bundle();
        args.putString(Const.INTENT_EXTRA_TOPIC, mTopicName);
        args.putInt(Const.INTENT_EXTRA_SEQ, mSeq);

        if (fragment.getArguments() != null) {
            fragment.getArguments().putAll(args);
        } else {
            fragment.setArguments(args);
        }

        FragmentTransaction trx = fm.beginTransaction();
        if (!fragment.isAdded()) {
            trx = trx.replace(R.id.contentFragment, fragment, tag)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        } else if (!fragment.isVisible()) {
            trx = trx.show(fragment);
        }

        if (!trx.isEmpty()) {
            trx.commit();
        }
    }

    private void topicAttach() {
        if (mTopic.isAttached()) {
            mTopic.videoCallRinging(mSeq);
            return;
        }

        if (!mTinode.isAuthenticated()) {
            // If connection is not ready, wait for completion. This method will be called again
            // from the onLogin callback;
            Cache.getTinode().reconnectNow(true, false, false);
            return;
        }

        Topic.MetaGetBuilder builder = mTopic.getMetaGetBuilder()
                .withDesc()
                .withLaterData()
                .withDel();

        mTopic.subscribe(null, builder.build())
                .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        mTopic.videoCallRinging(mSeq);
                        return null;
                    }
                })
                .thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        if (err instanceof AlreadySubscribedException) {
                            mTopic.videoCallRinging(mSeq);
                        } else {
                            Log.w(TAG, "Subscribe failed", err);
                            declineCall();
                        }
                        return null;
                    }
                });
    }

    private class EventListener implements Tinode.EventListener {
        @Override
        public void onLogin(int code, String txt) {
            if (code < ServerMessage.STATUS_MULTIPLE_CHOICES) {
                topicAttach();
            } else {
                declineCall();
            }
        }
    }
}
