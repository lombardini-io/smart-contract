package paddle.util;

import im.mak.paddle.Account;
import im.mak.paddle.api.StateChanges;
import im.mak.paddle.api.deser.ScriptTransfer;

public class Changes extends StateChanges {

    public String address = "";

    public static Changes of(Account account, StateChanges stateChanges) {
        Changes changes = new Changes();
        changes.data = stateChanges.data;
        changes.transfers = stateChanges.transfers;
        changes.address = account.address();
        return changes;
    }

    public long start() {
        return data.stream().filter(e -> e.key.contains("start_of_" + address)).findFirst().get().asInteger();
    }

    public long graceEnd() {
        return data.stream().filter(e -> e.key.contains("end_of_grace_of_" + address)).findFirst().get().asInteger();
    }

    public long interestEnd() {
        return data.stream().filter(e -> e.key.contains("end_of_interest_of_" + address)).findFirst().get().asInteger();
    }

    public long burndownEnd() {
        return data.stream().filter(e -> e.key.contains("end_of_burndown_of_" + address)).findFirst().get().asInteger();
    }

    public long rate() {
        return data.stream().filter(e -> e.key.contains("rate_of_" + address)).findFirst().get().asInteger();
    }

    public long deposit() {
        return data.stream().filter(e -> e.key.contains("deposit_of_" + address)).findFirst().get().asInteger();
    }

    public long lend() {
        return data.stream().filter(e -> e.key.contains("lend_of_" + address)).findFirst().get().asInteger();
    }

    public boolean registeredTx(String txId) {
        return data.stream().filter(e -> e.key.equals("registered_return_of_" + txId)).findFirst().get().asBoolean();
    }

    public ScriptTransfer transferToUser() {
        return transfers.stream().filter(t -> t.address.equals(address)).findFirst().get();
    }

    public ScriptTransfer transferToAnother() {
        return transfers.stream().filter(t -> !t.address.equals(address)).findFirst().get();
    }

}
