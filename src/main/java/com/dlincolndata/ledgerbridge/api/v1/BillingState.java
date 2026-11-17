package com.dlincolndata.ledgerbridge.api.v1;
import java.util.Set;

public enum BillingState {
    RECEIVED, VALIDATED, DISPATCHING, POSTED, RETRY_PENDING, REJECTED, SUPERSEDED, DEAD_LETTERED;
    public boolean canTransitionTo(BillingState t) {
        return switch (this) {
            case RECEIVED -> Set.of(VALIDATED, REJECTED).contains(t);
            case VALIDATED -> Set.of(DISPATCHING, REJECTED).contains(t);
            case DISPATCHING -> Set.of(POSTED, RETRY_PENDING, DEAD_LETTERED).contains(t);
            case RETRY_PENDING -> Set.of(DISPATCHING, DEAD_LETTERED).contains(t);
            case POSTED -> Set.of(SUPERSEDED).contains(t);
            case REJECTED, SUPERSEDED, DEAD_LETTERED -> false;
        };
    }
}
