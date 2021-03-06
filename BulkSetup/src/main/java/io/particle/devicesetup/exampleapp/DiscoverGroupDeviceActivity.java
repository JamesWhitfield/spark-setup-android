package io.particle.devicesetup.exampleapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.Toast;

import com.squareup.phrase.Phrase;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.particle.android.sdk.accountsetup.LoginActivity;
import io.particle.android.sdk.cloud.ParticleCloud;
import io.particle.android.sdk.devicesetup.commands.CommandClient;
import io.particle.android.sdk.devicesetup.commands.DeviceIdCommand;
import io.particle.android.sdk.devicesetup.commands.InterfaceBindingSocketFactory;
import io.particle.android.sdk.devicesetup.commands.PublicKeyCommand;
import io.particle.android.sdk.devicesetup.commands.SetCommand;
import io.particle.android.sdk.devicesetup.loaders.WifiScanResultLoader;
import io.particle.android.sdk.devicesetup.model.ScanResultNetwork;
import io.particle.android.sdk.devicesetup.model.WifiNetwork;
import io.particle.android.sdk.devicesetup.setupsteps.SetupStepException;
import io.particle.android.sdk.devicesetup.ui.ConnectToApFragment;
import io.particle.android.sdk.devicesetup.ui.DeviceSetupState;
import io.particle.android.sdk.devicesetup.ui.DiscoverDeviceActivity;
import io.particle.android.sdk.devicesetup.ui.GetReadyActivity;
import io.particle.android.sdk.devicesetup.ui.RequiresWifiScansActivity;
import io.particle.android.sdk.devicesetup.ui.SelectNetworkActivity;
import io.particle.android.sdk.utils.Crypto;
import io.particle.android.sdk.utils.EZ;
import io.particle.android.sdk.utils.SoftAPConfigRemover;
import io.particle.android.sdk.utils.TLog;
import io.particle.android.sdk.utils.WiFi;
import io.particle.android.sdk.utils.ui.Ui;
import io.particle.android.sdk.utils.ui.WebViewActivity;

import static io.particle.android.sdk.utils.Py.truthy;

/**
 * Created by jwhit on 07/03/2016.
 */
public class DiscoverGroupDeviceActivity extends RequiresWifiScansActivity
        implements WifiListGroupFragment.Client<ScanResultNetwork>,
        ConnectToApFragment.Client {


    private static final int MAX_NUM_DISCOVER_PROCESS_ATTEMPTS = 10;
    private static final long CONNECT_TO_DEVICE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);


    private static final TLog log = TLog.get(DiscoverDeviceActivity.class);


    private WifiManager wifiManager;
    private ParticleCloud sparkCloud;
    private DiscoverProcessWorker discoverProcessWorker;
    private SoftAPConfigRemover softAPConfigRemover;

    private WifiListGroupFragment wifiListGroupFragment;
    private ProgressDialog connectToApSpinnerDialog;

    private AsyncTask<Void, Void, SetupStepException> connectToApTask;
    private boolean isResumed = false;

    private int discoverProcessAttempts = 0;

    // FIXME: UGH. Figure out a way to pass this info along without making it
    // into class-wide mutable state.
    private String currentSSID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover_group_device);

        softAPConfigRemover = new SoftAPConfigRemover(this);
        softAPConfigRemover.removeAllSoftApConfigs();
        softAPConfigRemover.reenableWifiNetworks();

        DeviceSetupState.previouslyConnectedWifiNetwork = WiFi.getCurrentlyConnectedSSID(this);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        sparkCloud = ParticleCloud.get(this);

        wifiListGroupFragment = Ui.findFrag(this, R.id.wifi_list_group_fragment);
        ConnectToApFragment.ensureAttached(this);

        resetWorker();

        Ui.setText(this, io.particle.android.sdk.devicesetup.R.id.wifi_list_header,
                Phrase.from(this, R.string.wifi_list_header_group_text)
                        .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                        .format()
        );
        Ui.setText(this, io.particle.android.sdk.devicesetup.R.id.msg_device_not_listed,
                Phrase.from(this, io.particle.android.sdk.devicesetup.R.string.msg_device_not_listed)
                        .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                        .put("setup_button_identifier", getString(io.particle.android.sdk.devicesetup.R.string.mode_button_name))
                        .put("indicator_light", getString(io.particle.android.sdk.devicesetup.R.string.indicator_light))
                        .put("indicator_light_setup_color_name", getString(io.particle.android.sdk.devicesetup.R.string.listen_mode_led_color_name))
                        .format()
        );

        Ui.setTextFromHtml(this, io.particle.android.sdk.devicesetup.R.id.action_troubleshooting, io.particle.android.sdk.devicesetup.R.string.troubleshooting).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Uri uri = Uri.parse(v.getContext().getString(io.particle.android.sdk.devicesetup.R.string.troubleshooting_uri));
                        startActivity(WebViewActivity.buildIntent(v.getContext(), uri));
                    }
                }
        );

        Ui.setText(this, io.particle.android.sdk.devicesetup.R.id.logged_in_as,
                Phrase.from(this, io.particle.android.sdk.devicesetup.R.string.you_are_logged_in_as)
                        .put("username", sparkCloud.getLoggedInUsername())
                        .format()
        );

        Ui.findView(this, io.particle.android.sdk.devicesetup.R.id.action_log_out).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sparkCloud.logOut();
                log.i("logged out, username is: " + sparkCloud.getLoggedInUsername());
                startActivity(new Intent(DiscoverGroupDeviceActivity.this, LoginActivity.class));
                finish();
            }
        });

        Ui.findView(this, R.id.action_group_setup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                log.i("Group setup, Start group setup");
                int j= 0;
                j = wifiListGroupFragment.getListView().getCheckedItemCount();
                if(j > 0){
                    createWifiConfigArray(wifiListGroupFragment.getListView().getCheckedItemPositions());
                    startActivity(new Intent(getApplicationContext(),SelectNetworkForGroupActivity.class));
                }else{
                    Toast.makeText(getApplicationContext(),"Please select devices",Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!wifiManager.isWifiEnabled()) {
            onWifiDisabled();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
    }

    private void createWifiConfigArray(SparseBooleanArray checkedItemPositions){
        ArrayList<WifiNetwork> wifiNetworkArrayList = new ArrayList<>();
        for (int i = 0; i < checkedItemPositions.size(); i++) {
            int lpos = checkedItemPositions.keyAt(i);
            boolean ischecked = checkedItemPositions.get(lpos);
            if(ischecked){
                WifiNetwork wifiNetwork = (WifiNetwork) wifiListGroupFragment.getListView().getAdapter().getItem(checkedItemPositions.keyAt(i));
                wifiNetworkArrayList.add(wifiNetwork);
            }
        }
        ((ApplicationController)getApplication()).photonSetupGroup.clear();
        ((ApplicationController)getApplication()).photonSetupGroup.addAll(wifiNetworkArrayList);
    }

    private void resetWorker() {
        discoverProcessWorker = new DiscoverProcessWorker(
                CommandClient.newClientUsingDefaultSocketAddress());
    }

    // FIXME: do we even want to do this...?
    private void onWifiDisabled() {
        log.d("Wi-Fi disabled; prompting user");
        new AlertDialog.Builder(this)
                .setTitle(io.particle.android.sdk.devicesetup.R.string.wifi_required)
                .setPositiveButton(io.particle.android.sdk.devicesetup.R.string.enable_wifi, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        log.i("Enabling Wi-Fi at the user's request.");
                        wifiManager.setWifiEnabled(true);
                        wifiListGroupFragment.scanAsync();
                    }
                })
                .setNegativeButton(io.particle.android.sdk.devicesetup.R.string.exit_setup, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .show();
    }

    @Override
    public void onNetworkSelected(ScanResultNetwork selectedNetwork) {
        WifiConfiguration wifiConfig = ConnectToApFragment.buildUnsecuredConfig(
                selectedNetwork.getSsid(), false);
        currentSSID = selectedNetwork.getSsid();
        connectToSoftAp(wifiConfig);
    }

    private void connectToSoftAp(WifiConfiguration config) {
        discoverProcessAttempts++;
        softAPConfigRemover.onSoftApConfigured(config.SSID);
        ConnectToApFragment.get(this).connectToAP(config, CONNECT_TO_DEVICE_TIMEOUT_MILLIS);
        showProgressDialog();
    }

    @Override
    public Loader<Set<ScanResultNetwork>> createLoader(int id, Bundle args) {
        return new WifiScanResultLoader(this);
    }

    @Override
    public void onLoadFinished() {
        // no-op
    }

    @Override
    public String getListEmptyText() {
        return Phrase.from(this, io.particle.android.sdk.devicesetup.R.string.empty_soft_ap_list_text)
                .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format().toString();
    }

    @Override
    public int getAggroLoadingTimeMillis() {
        return 5000;
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

    private void showProgressDialog() {
        wifiListGroupFragment.stopAggroLoading();

        String msg = Phrase.from(this, io.particle.android.sdk.devicesetup.R.string.connecting_to_soft_ap)
                .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format().toString();



        connectToApSpinnerDialog = new ProgressDialog(this);
        connectToApSpinnerDialog.setMessage(msg);
        connectToApSpinnerDialog.setCancelable(false);
        connectToApSpinnerDialog.setIndeterminate(true);
        connectToApSpinnerDialog.show();
    }

    private void hideProgressDialog() {
        wifiListGroupFragment.startAggroLoading();
        if (connectToApSpinnerDialog != null) {
            if (!isFinishing()) {
                connectToApSpinnerDialog.dismiss();
            }
            connectToApSpinnerDialog = null;
        }
    }

    private void startConnectWorker() {
        // first, make sure we haven't actually been called twice...
        if (connectToApTask != null) {
            log.d("Already running connect worker " + connectToApTask + ", refusing to start another");
            return;
        }

        wifiListGroupFragment.stopAggroLoading();
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
                            new InterfaceBindingSocketFactory(DiscoverGroupDeviceActivity.this, currentSSID));
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
                    // no exceptions thrown, huzzah
                    hideProgressDialog();
                    startActivity(new Intent(DiscoverGroupDeviceActivity.this, SelectNetworkActivity.class));
                    finish();

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

    private boolean canStartProcessAgain() {
        return discoverProcessAttempts < MAX_NUM_DISCOVER_PROCESS_ATTEMPTS;
    }

    private void onMaxAttemptsReached() {
        if (!isResumed) {
            finish();
            return;
        }

        String errorMsg = Phrase.from(this, io.particle.android.sdk.devicesetup.R.string.unable_to_connect_to_soft_ap)
                .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format().toString();

        new AlertDialog.Builder(this)
                .setTitle(io.particle.android.sdk.devicesetup.R.string.error)
                .setMessage(errorMsg)
                .setPositiveButton(io.particle.android.sdk.devicesetup.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        startActivity(new Intent(DiscoverGroupDeviceActivity.this, GetReadyActivity.class));
                        finish();
                    }
                })
                .show();
    }

    private void onDeviceClaimedByOtherUser() {
        String dialogMsg = String.format("This %s is owned by another user.  Change owner to %s?",
                getString(io.particle.android.sdk.devicesetup.R.string.device_name), sparkCloud.getLoggedInUsername());

        new AlertDialog.Builder(this)
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
                        dialog.dismiss();
                        startActivity(new Intent(DiscoverGroupDeviceActivity.this, GetReadyActivity.class));
                        finish();
                    }
                })
                .show();
    }


    // FIXME: Even before it's done, I am pretty sure this will need
    // to go through a round of "solve et coagula" before it's
    // really right, at least maintenance-wise.
    // FIXME: this naming is no longer really applicable.
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

}