package io.particle.devicesetup.exampleapp;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.phrase.Phrase;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

import io.particle.android.sdk.cloud.ParticleCloud;
import io.particle.android.sdk.devicesetup.commands.CommandClient;
import io.particle.android.sdk.devicesetup.commands.DeviceIdCommand;
import io.particle.android.sdk.devicesetup.commands.InterfaceBindingSocketFactory;
import io.particle.android.sdk.devicesetup.commands.PublicKeyCommand;
import io.particle.android.sdk.devicesetup.commands.SetCommand;
import io.particle.android.sdk.devicesetup.setupsteps.SetupStepException;
import io.particle.android.sdk.devicesetup.ui.ConnectToApFragment;
import io.particle.android.sdk.devicesetup.ui.DeviceSetupState;
import io.particle.android.sdk.devicesetup.ui.GetReadyActivity;
import io.particle.android.sdk.devicesetup.ui.SelectNetworkActivity;
import io.particle.android.sdk.utils.Crypto;
import io.particle.android.sdk.utils.EZ;
import io.particle.android.sdk.utils.SoftAPConfigRemover;
import io.particle.android.sdk.utils.TLog;

import static io.particle.android.sdk.utils.Py.truthy;

/**
 * Created by jwhit on 08/03/2016.
 */
//TODO for each device
    //TODO connect over wifi
    //TODO what to do when connection fails
        //TODO  move to next item
    //TODO send item
public class ConnectingGroupFragment extends Fragment implements SetupGroupActivity.ConnectingInterface{



    public void setPassword(String password) {
        this.password = password;
    }
    private static final TLog log = TLog.get(ConnectingGroupFragment.class);
    String password;
    private SoftAPConfigRemover softAPConfigRemover;
    private int discoverProcessAttempts = 0;
    private static final int MAX_NUM_DISCOVER_PROCESS_ATTEMPTS = 5;
    private static final long CONNECT_TO_DEVICE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private ProgressDialog connectToApSpinnerDialog;
    private boolean isResumed = false;
    private DiscoverProcessWorker discoverProcessWorker;
    private AsyncTask<Void, Void, SetupStepException> connectToApTask;
    private ParticleCloud sparkCloud;
    private String currentSSID;

    public static ConnectingGroupFragment newInstance() {

        ConnectingGroupFragment connectingGroupFragment = new ConnectingGroupFragment();
        return connectingGroupFragment;
    }

    public static ConnectingGroupFragment newInstance(String password) {

        ConnectingGroupFragment connectingGroupFragment = new ConnectingGroupFragment();
        connectingGroupFragment.setPassword(password);

        return connectingGroupFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        //Set initial variables
        sparkCloud = ParticleCloud.get(getContext());

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_group_connect, container, false);

        ApplicationController applicationController = (ApplicationController) getActivity().getApplication();
        Log.i("Hello","Connecting:" + applicationController.photonSetupGroup.size());
        Log.i("Hello","To:" + applicationController.selectedNetwork.getSsid());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        /*WifiConfiguration wifiConfig = ConnectToApFragment.buildUnsecuredConfig(
                selectedNetwork.getSsid(), false);
        currentSSID = selectedNetwork.getSsid();
        connectToSoftAp(wifiConfig);*/

        beginConnectingDevices();
    }

    private void beginConnectingDevices() {
        //TODO begin connect the devices
    }

    private void connectToSoftAp(WifiConfiguration config) {
        discoverProcessAttempts++;
        softAPConfigRemover.onSoftApConfigured(config.SSID);
        ConnectToApFragment.get(this.getActivity()).connectToAP(config, CONNECT_TO_DEVICE_TIMEOUT_MILLIS);
        showProgressDialog();
    }

    private void showProgressDialog() {

        String msg = Phrase.from(getContext(), io.particle.android.sdk.devicesetup.R.string.connecting_to_soft_ap)
                .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format().toString();



        connectToApSpinnerDialog = new ProgressDialog(getContext());
        connectToApSpinnerDialog.setMessage(msg);
        connectToApSpinnerDialog.setCancelable(false);
        connectToApSpinnerDialog.setIndeterminate(true);
        connectToApSpinnerDialog.show();
    }


    @Override
    public void onApConnectionSuccessful(WifiConfiguration config) {
        startConnectWorker();
    }

    @Override
    public void onApConnectionFailed(WifiConfiguration config) {
        hideProgressDialog();

        if (!canStartProcessAgain()) {
            onMaxAttemptsReached();
        } else {
            connectToSoftAp(config);
        }
    }

    private boolean canStartProcessAgain() {
        return discoverProcessAttempts < MAX_NUM_DISCOVER_PROCESS_ATTEMPTS;
    }

    private void startConnectWorker() {
        // first, make sure we haven't actually been called twice...
        if (connectToApTask != null) {
            log.d("Already running connect worker " + connectToApTask + ", refusing to start another");
            return;
        }

//        wifiListFragment.stopAggroLoading();
        // FIXME: verify first that we're still connected to the intended network
        if (!canStartProcessAgain()) {
            hideProgressDialog();
            onMaxAttemptsReached();
            return;
        }

        discoverProcessAttempts++;

        // Kind of lame; this just has doInBackground() return null on success, or if an
        // exception was thrown, it passes that along instead to indicate failure.
        connectToApTask = new AsyncTask<Void, Void, SetupStepException>() {

            @Override
            protected SetupStepException doInBackground(Void... voids) {
                try {
                    // including this sleep because without it,
                    // we seem to attempt a socket connection too early,
                    // and it makes the process time out
                    log.d("Waiting a couple seconds before trying the socket connection...");
                    EZ.threadSleep(2000);

                    discoverProcessWorker.doTheThing(
                            new InterfaceBindingSocketFactory(getContext(), currentSSID));
                    return null;

                } catch (SetupStepException e) {
                    log.d("Setup exception thrown: ", e);
                    return e;

                }
            }

            @Override
            protected void onPostExecute(SetupStepException error) {
                connectToApTask = null;
                if (error == null) {
                    //TODO now connected send wifi data
                    //TODO then move to next item
                    // no exceptions thrown, huzzah
                    hideProgressDialog();
                    /*startActivity(new Intent(DiscoverDeviceActivity.this, SelectNetworkActivity.class));
                    finish();
*/
                } else if (error instanceof DeviceAlreadyClaimed) {
                    hideProgressDialog();
                    onDeviceClaimedByOtherUser();

                } else {
                    // nope, do it all over again.
                    // FIXME: this might be a good time to display some feedback...
                    startConnectWorker();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void hideProgressDialog() {
        if (connectToApSpinnerDialog != null) {
            if (!getActivity().isFinishing()) {
                connectToApSpinnerDialog.dismiss();
            }
            connectToApSpinnerDialog = null;
        }
    }

    private void onMaxAttemptsReached() {
        //TODO move to next item
        /*if (!isResumed) {
            finish();
            return;
        }*/

        /*String errorMsg = Phrase.from(getContext(), io.particle.android.sdk.devicesetup.R.string.unable_to_connect_to_soft_ap)
                .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format().toString();

        new AlertDialog.Builder(getContext())
                .setTitle(io.particle.android.sdk.devicesetup.R.string.error)
                .setMessage(errorMsg)
                .setPositiveButton(io.particle.android.sdk.devicesetup.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        startActivity(new Intent(DiscoverDeviceActivity.this, GetReadyActivity.class));
                        finish();
                    }
                })
                .show();*/
    }

    static class DiscoverProcessWorker {

        private final CommandClient client;

        private String detectedDeviceID;

        private volatile boolean isDetectedDeviceClaimed;
        private volatile boolean gotOwnershipInfo;
        private volatile boolean needToClaimDevice;


        DiscoverProcessWorker(CommandClient client) {
            this.client = client;
        }

        // FIXME: all this should probably become a list of commands to run in a queue,
        // each with shortcut conditions for when they've already been fulfilled, instead of
        // this if-else/try-catch ladder.
        public void doTheThing(InterfaceBindingSocketFactory socketFactory) throws SetupStepException {
            // 1. get device ID
            if (!truthy(detectedDeviceID)) {
                try {
                    DeviceIdCommand.Response response = client.sendCommandAndReturnResponse(
                            new DeviceIdCommand(), DeviceIdCommand.Response.class, socketFactory);
                    detectedDeviceID = response.deviceIdHex.toLowerCase();
                    DeviceSetupState.deviceToBeSetUpId = detectedDeviceID;
                    isDetectedDeviceClaimed = truthy(response.isClaimed);
                } catch (IOException e) {
                    throw new SetupStepException("Process died while trying to get the device ID", e);
                }
            }

            // 2. Get public key
            if (DeviceSetupState.publicKey == null) {
                try {
                    DeviceSetupState.publicKey = getPublicKey(socketFactory);
                } catch (Crypto.CryptoException e) {
                    throw new SetupStepException("Unable to get public key: ", e);

                } catch (IOException e) {
                    throw new SetupStepException("Error while fetching public key: ", e);
                }
            }

            // 3. check ownership
            //
            // all cases:
            // (1) device not claimed `c=0` — device should also not be in list from API => mobile
            //      app assumes user is claiming
            // (2) device claimed `c=1` and already in list from API => mobile app does not ask
            //      user about taking ownership because device already belongs to this user
            // (3) device claimed `c=1` and NOT in the list from the API => mobile app asks whether
            //      use would like to take ownership
            if (!gotOwnershipInfo) {
                needToClaimDevice = false;

                // device was never claimed before - so we need to claim it anyways
                if (!isDetectedDeviceClaimed) {
                    setClaimCode(socketFactory);
                    needToClaimDevice = true;

                } else {
                    boolean deviceClaimedByUser = false;
                    for (String deviceId : DeviceSetupState.claimedDeviceIds) {
                        if (deviceId.equalsIgnoreCase(detectedDeviceID)) {
                            deviceClaimedByUser = true;
                            break;
                        }
                    }
                    gotOwnershipInfo = true;

                    if (isDetectedDeviceClaimed && !deviceClaimedByUser) {
                        // This device is already claimed by someone else. Ask the user if we should
                        // change ownership to the current logged in user, and if so, set the claim code.

                        throw new DeviceAlreadyClaimed("Device already claimed by another user");

                    } else {
                        // Success: no exception thrown, this part of the process is complete.
                        // Let the caller continue on with the setup process.
                        return;
                    }
                }

            } else {
                if (needToClaimDevice) {
                    setClaimCode(socketFactory);
                }
                // Success: no exception thrown, the part of the process is complete.  Let the caller
                // continue on with the setup process.
                return;
            }
        }

        private void setClaimCode(InterfaceBindingSocketFactory socketFactory)
                throws SetupStepException {
            try {
                log.d("Setting claim code using code: " + DeviceSetupState.claimCode);

                SetCommand.Response response = client.sendCommandAndReturnResponse(
                        new SetCommand("cc", StringUtils.remove(DeviceSetupState.claimCode, "\\")),
                        SetCommand.Response.class, socketFactory);

                if (truthy(response.responseCode)) {
                    // a non-zero response indicates an error, ala UNIX return codes
                    throw new SetupStepException("Received non-zero return code from set command: "
                            + response.responseCode);
                }

                log.d("Successfully set claim code");

            } catch (IOException e) {
                throw new SetupStepException(e);
            }
        }

        private PublicKey getPublicKey(InterfaceBindingSocketFactory socketFactory)
                throws Crypto.CryptoException, IOException {
            PublicKeyCommand.Response response = this.client.sendCommandAndReturnResponse(
                    new PublicKeyCommand(), PublicKeyCommand.Response.class, socketFactory);

            return Crypto.readPublicKeyFromHexEncodedDerString(response.publicKey);
        }
    }


    // FIXME: remove this if we break down the worker above into Steps
    // no data to pass along with this at the moment, I just want to specify
    // that this isn't an error which should necessarily count against retries.
    static class DeviceAlreadyClaimed extends SetupStepException {
        public DeviceAlreadyClaimed(String msg, Throwable throwable) {
            super(msg, throwable);
        }

        public DeviceAlreadyClaimed(String msg) {
            super(msg);
        }

        public DeviceAlreadyClaimed(Throwable throwable) {
            super(throwable);
        }
    }

    private void resetWorker() {
        discoverProcessWorker = new DiscoverProcessWorker(
                CommandClient.newClientUsingDefaultSocketAddress());
    }

    private void onDeviceClaimedByOtherUser() {
        String dialogMsg = String.format("This %s is owned by another user.  Change owner to %s?",
                getString(io.particle.android.sdk.devicesetup.R.string.device_name), sparkCloud.getLoggedInUsername());

        new AlertDialog.Builder(getContext())
                .setTitle("Change owner?")
                .setMessage(dialogMsg)
                .setPositiveButton("Change owner", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        log.i("Changing owner to " + sparkCloud.getLoggedInUsername());
//                        // FIXME: state mutation from another class.  Not pretty.
//                        // Fix this by breaking DiscoverProcessWorker down into Steps
                        resetWorker();
                        discoverProcessWorker.needToClaimDevice = true;
                        discoverProcessWorker.gotOwnershipInfo = true;
                        discoverProcessWorker.isDetectedDeviceClaimed = false;
                        DeviceSetupState.deviceNeedsToBeClaimed = true;

                        showProgressDialog();
                        startConnectWorker();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        /*dialog.dismiss();
                        startActivity(new Intent(DiscoverDeviceActivity.this, GetReadyActivity.class));
                        finish();*/
                        //TODO move to next item
                    }
                })
                .show();
    }
}
