package com.monkeyhand.puppet;

import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.RecyclerView;
import com.monkeyhand.puppet.media.VxCard;

/**
 * In-memory adapter keeps selected members of the group: initial members before the editing,
 * current members after editing. Some member could be non-removable.
 */
public class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.ViewHolder> {
    // List of initial group members.
    // Key: unique ID of the user.
    private final HashMap<String, Void> mInitialMembers;
    // List of current group members.
    private final ArrayList<Member> mCurrentMembers;

    // mCancelable means initial items can be removed too.
    // Newly added members can always be removed.
    private final boolean mCancelable;
    // Callback notify parent of the member removed.
    private final ClickListener mOnCancel;

    MembersAdapter(@Nullable ArrayList<Member> users, @Nullable ClickListener onCancel,
                   boolean cancelable) {
        setHasStableIds(true);

        mCancelable = cancelable;
        mOnCancel = onCancel;

        mInitialMembers = new HashMap<>();
        mCurrentMembers = new ArrayList<>();

        if (users != null) {
            for (Member user : users) {
                mInitialMembers.put(user.unique, null);
                mCurrentMembers.add(user);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false), viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.viewType == R.layout.group_member_chip) {
            holder.bind(position);
        }
    }

    private int getActualItemCount() {
        return mCurrentMembers.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (getActualItemCount() == 0) {
            return R.layout.group_member_chip_empty;
        }
        return R.layout.group_member_chip;
    }

    @Override
    public int getItemCount() {
        int count = getActualItemCount();
        return count == 0 ? 1 : count;
    }

    @Override
    public long getItemId(int pos) {
        if (getActualItemCount() == 0) {
            return "empty".hashCode();
        }
        return mCurrentMembers.get(pos).unique.hashCode();
    }

    void append(int pos, String unique, String displayName, String avatar) {
        append(new Member(pos, unique, displayName, avatar, true));
    }

    private void append(Member user) {
        // Ensure uniqueness.
        for (int i = 0; i < mCurrentMembers.size(); i++) {
            if (user.unique.equals(mCurrentMembers.get(i).unique)) {
                return;
            }
        }

        mCurrentMembers.add(user);
        notifyItemInserted(getItemCount() - 1);
    }

    boolean remove(@NonNull String unique) {
        // Check if the member is allowed to be removed.
        if (!mCancelable && mInitialMembers.containsKey(unique)) {
            return false;
        }

        for (int i = 0; i < mCurrentMembers.size(); i++) {
            Member m = mCurrentMembers.get(i);
            if (unique.equals(m.unique) && m.removable) {
                mCurrentMembers.remove(i);
                notifyItemRemoved(i);
                return true;
            }
        }
        return false;
    }

    String[] getAdded() {
        ArrayList<String> added = new ArrayList<>();
        for (Member user : mCurrentMembers) {
            if (!mInitialMembers.containsKey(user.unique)) {
                added.add(user.unique);
            }
        }
        return added.toArray(new String[]{});
    }

    String[] getRemoved() {
        ArrayList<String> removed = new ArrayList<>();
        HashMap<String, Object> current = new HashMap<>();
        // Index current members by unique value.
        for (Member user : mCurrentMembers) {
            current.put(user.unique, "");
        }

        for (String unique : mInitialMembers.keySet()) {
            if (!current.containsKey(unique)) {
                removed.add(unique);
            }
        }
        return removed.toArray(new String[]{});
    }

    interface ClickListener {
        void onClick(String unique, int pos);
    }

    static class Member {
        // Member position within the parent contacts adapter.
        int position;
        final String unique;
        final String displayName;
        Bitmap avatarBitmap;
        final Uri avatarUri;
        final Boolean removable;

        Member(int position, String unique, String displayName, String avatar, boolean removable) {
            this.position = position;
            this.unique = unique;
            this.removable = removable;
            this.displayName = displayName;
            this.avatarBitmap = null;
            if (avatar != null) {
                this.avatarUri = Uri.parse(avatar);
            } else {
                this.avatarUri = null;
            }
        }

        Member(int position, String unique, VxCard pub, boolean removable) {
            this.position = position;
            this.unique = unique;
            this.removable = removable;
            if (pub != null) {
                this.displayName = pub.fn;
                String ref = pub.getPhotoRef();
                if (ref != null) {
                    this.avatarUri = Uri.parse(ref);
                    this.avatarBitmap = null;
                } else {
                    this.avatarUri = null;
                    this.avatarBitmap = pub.getBitmap();
                }
            } else {
                this.displayName = null;
                this.avatarUri = null;
                this.avatarBitmap = null;
            }
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final int viewType;
        ImageView avatar;
        TextView displayName;
        AppCompatImageButton close;

        ViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);

            this.viewType = viewType;
            if (viewType == R.layout.group_member_chip) {
                avatar = itemView.findViewById(android.R.id.icon);
                displayName = itemView.findViewById(android.R.id.text1);
                close = itemView.findViewById(android.R.id.closeButton);
            }
        }

        void bind(int pos) {
            Member user = mCurrentMembers.get(pos);
            if (user.avatarBitmap != null) {
                avatar.setImageBitmap(user.avatarBitmap);
            } else if (user.avatarUri != null) {
                Picasso.get()
                        .load(user.avatarUri)
                        .placeholder(R.drawable.disk)
                        .error(R.drawable.ic_broken_image_round)
                        .into(avatar);
            } else {
                avatar.setImageDrawable(
                        UiUtils.avatarDrawable(avatar.getContext(), null,
                                user.displayName, user.unique, false));
            }

            displayName.setText(user.displayName);
            if (user.removable && (mCancelable || !mInitialMembers.containsKey(user.unique))) {
                close.setVisibility(View.VISIBLE);
                close.setOnClickListener(view -> {
                    // Position within the member adapter. Getting it here again (instead of reusing 'pos') because it
                    // may have changed since binding.
                    int position = getBindingAdapterPosition();
                    Member foundUser = mCurrentMembers.remove(position);
                    if (mOnCancel != null) {
                        // Notify parent ContactsAdapter that the user was removed.
                        mOnCancel.onClick(foundUser.unique, foundUser.position);
                    }
                    notifyItemRemoved(position);
                });
            } else {
                close.setVisibility(View.GONE);
            }
        }
    }
}
