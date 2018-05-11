package com.oberasoftware.jasdb.core.index.btreeplus.locking;

/**
* @author Renze de Vries
* Date: 6-6-12
* Time: 21:50
*/
public enum LockIntentType {
    READ(new ReadIntent()),
    WRITE_EXCLUSIVE(new WriteIntentExclusive()),
    UPDATE(new UpdateIntent()),
    LEAVELOCK_OPTIMISTIC(new OptimisticLeaveLockIntent());

    private LockIntent intent;

    LockIntentType(LockIntent intent) {
        this.intent = intent;
    }

    public LockIntent getIntent() {
        return this.intent;
    }
}
