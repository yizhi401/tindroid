package com.monkeyhand.puppet.db;

import com.monkeyhand.puppet.LocalData;
import com.monkeyhand.puppet.model.Subscription;

/**
 * Subscription record
 */
public class StoredSubscription implements LocalData.Payload {
    public long id;
    public long topicId;
    public long userId;
    public BaseDb.Status status;

    public static long getId(Subscription sub) {
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        return ss == null ? -1 : ss.id;
    }
}
