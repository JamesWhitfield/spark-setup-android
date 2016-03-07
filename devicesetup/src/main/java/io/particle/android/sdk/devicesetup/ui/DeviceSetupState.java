package io.particle.android.sdk.devicesetup.ui;


import java.security.PublicKey;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

// FIXME: Statically defined, global, mutable state...  refactor this thing into oblivion soon.
public class DeviceSetupState {

    public static final Set<String> claimedDeviceIds = new ConcurrentSkipListSet<>();
    public static volatile String previouslyConnectedWifiNetwork;
    public static volatile String claimCode;
    public static volatile PublicKey publicKey;
    public static volatile String deviceToBeSetUpId;
    public static volatile boolean deviceNeedsToBeClaimed = true;

    public static void reset() {
        claimCode = null;
        claimedDeviceIds.clear();
        publicKey = null;
        deviceToBeSetUpId = null;
        deviceNeedsToBeClaimed = true;
        previouslyConnectedWifiNetwork = null;
    }
}
