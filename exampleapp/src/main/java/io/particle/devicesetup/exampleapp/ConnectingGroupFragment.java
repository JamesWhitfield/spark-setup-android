package io.particle.devicesetup.exampleapp;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.phrase.Phrase;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.particle.android.sdk.cloud.ParticleCloud;
import io.particle.android.sdk.cloud.ParticleCloudException;
import io.particle.android.sdk.cloud.ParticleCloudSDK;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.devicesetup.SetupProcessException;
import io.particle.android.sdk.devicesetup.commands.CommandClient;
import io.particle.android.sdk.devicesetup.commands.DeviceIdCommand;
import io.particle.android.sdk.devicesetup.commands.InterfaceBindingSocketFactory;
import io.particle.android.sdk.devicesetup.commands.PublicKeyCommand;
import io.particle.android.sdk.devicesetup.commands.ScanApCommand;
import io.particle.android.sdk.devicesetup.commands.SetCommand;
import io.particle.android.sdk.devicesetup.model.WifiNetwork;
import io.particle.android.sdk.devicesetup.setupsteps.CheckIfDeviceClaimedStep;
import io.particle.android.sdk.devicesetup.setupsteps.ConfigureAPStep;
import io.particle.android.sdk.devicesetup.setupsteps.ConnectDeviceToNetworkStep;
import io.particle.android.sdk.devicesetup.setupsteps.EnsureSoftApNotVisible;
import io.particle.android.sdk.devicesetup.setupsteps.SetupStep;
import io.particle.android.sdk.devicesetup.setupsteps.SetupStepException;
import io.particle.android.sdk.devicesetup.setupsteps.SetupStepsRunnerTask;
import io.particle.android.sdk.devicesetup.setupsteps.StepConfig;
import io.particle.android.sdk.devicesetup.setupsteps.StepProgress;
import io.particle.android.sdk.devicesetup.setupsteps.WaitForCloudConnectivityStep;
import io.particle.android.sdk.devicesetup.setupsteps.WaitForDisconnectionFromDeviceStep;
import io.particle.android.sdk.devicesetup.ui.ConnectToApFragment;
import io.particle.android.sdk.devicesetup.ui.DeviceSetupState;
import io.particle.android.sdk.devicesetup.ui.DiscoverDeviceActivity;
import io.particle.android.sdk.devicesetup.ui.GetReadyActivity;
import io.particle.android.sdk.devicesetup.ui.SelectNetworkActivity;
import io.particle.android.sdk.devicesetup.ui.SuccessActivity;
import io.particle.android.sdk.utils.CoreNameGenerator;
import io.particle.android.sdk.utils.Crypto;
import io.particle.android.sdk.utils.EZ;
import io.particle.android.sdk.utils.SoftAPConfigRemover;
import io.particle.android.sdk.utils.TLog;
import io.particle.android.sdk.utils.WiFi;
import io.particle.android.sdk.utils.ui.Ui;

import static io.particle.android.sdk.utils.Py.list;
import static io.particle.android.sdk.utils.Py.set;
import static io.particle.android.sdk.utils.Py.truthy;

/**
 * Created by jwhit on 08/03/2016.
 */
//TODO for each device
    //TODO connect over wifi
    //TODO what to do when connection fails
        //TODO  move to next item
    //TODO send item
public class ConnectingGroupFragment extends Fragment {

    private ConnectingProcessWorkerTask connectingProcessWorkerTask;
    private static final int MAX_NUM_DISCOVER_PROCESS_ATTEMPTS = 5;
    private static final int MAX_RETRIES_CONFIGURE_AP = 5;
    private int discoverProcessAttempts = 0;
    private static final int MAX_RETRIES_CONNECT_AP = 5;
    private static final int MAX_RETRIES_DISCONNECT_FROM_DEVICE = 5;
    private static final int MAX_RETRIES_CLAIM = 5;
    ConnectToApDialogFragment dialogFragment;
    private String password;
    private WifiManager wifiManager;
    private DiscoverProcessWorker discoverProcessWorker;
    private BroadcastReceiver wifiStateChangeListener;
    private WifiStateChangeLogger wifiStateChangeLogger;
    private Context appContext;
    private SoftAPConfigRemover softAPConfigRemover;
    private static final long CONNECT_TO_DEVICE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private boolean isResumed = false;
    private AsyncTask<Void, Void, SetupStepException> connectToApTask;
    private String currentSSID;
    private static final TLog log = TLog.get(ConnectingGroupFragment.class);
    TextView tvCurrentAction;
    TextView tvResults;
    private CommandClient client;
    private PublicKey publicKey;
    private String deviceSoftApSsid;
    private ParticleCloud sparkCloud;
    private String deviceId;
    private boolean needToClaimDevice;
    // for handling through the runloop
    private Handler mainThreadHandler;
    private Runnable onTimeoutRunnable;
    private List<Runnable> setupRunnables = list();
    ApplicationController applicationController;
    private String networkSecretPlaintext;
    private Button btnCancel;
    private TextView tvCurrentDevice;
    private ScanApCommand.Scan networkToConnectTo;
    private ProgressBar progressBar;
    private ListView lvResults;
    List<String> your_array_list;
    private ArrayList<WifiNetwork> wifiNetworkArrayList;
    private ArrayAdapter<String> arrayAdapter;

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

        softAPConfigRemover = new SoftAPConfigRemover(getContext());
        softAPConfigRemover.removeAllSoftApConfigs();

        appContext = getActivity().getApplicationContext();
        wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        mainThreadHandler = new Handler();
        wifiStateChangeLogger = new WifiStateChangeLogger();
        applicationController = (ApplicationController) getActivity().getApplication();
        wifiNetworkArrayList = new ArrayList<WifiNetwork>();
                wifiNetworkArrayList.addAll(applicationController.photonSetupGroup);
        resetWorker();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_group_connect, container, false);

        Log.i("Hello","Connecting:" + applicationController.photonSetupGroup.size());
        Log.i("Hello","To:" + applicationController.selectedNetwork.getSsid());

        TextView tvConfig = (TextView) view.findViewById(R.id.tvconfig);
         progressBar = (ProgressBar) view.findViewById(R.id.pbProgress);
        tvCurrentDevice = (TextView) view.findViewById(R.id.tvCurrentDevice);
         lvResults = (ListView) view.findViewById(R.id.lvResults);
        btnCancel = (Button) view.findViewById(R.id.btnCancel);

        tvConfig.setText("Photons to setup: " + applicationController.photonSetupGroup.size() + "\n"
                + "Target Network: " + applicationController.selectedNetwork.getSsid());

        your_array_list = new ArrayList<String>();
        arrayAdapter = new ArrayAdapter<String>(
                getContext(),
                android.R.layout.simple_list_item_1,
                your_array_list );
        lvResults.setAdapter(arrayAdapter);


        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO proper cancel for whole process needed
                if(connectingProcessWorkerTask != null) {
                    connectingProcessWorkerTask.cancel(false);
                }
                getActivity().finish();
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        /*WifiConfiguration wifiConfig = ConnectToApFragment.buildUnsecuredConfig(
                selectedNetwork.getSsid(), false);
        currentSSID = selectedNetwork.getSsid();
        connectToSoftAp(wifiConfig);*/
        if (!wifiManager.isWifiEnabled()) {
            onWifiDisabled();
        }
        beginConnectingDevices();
    }

    private void beginConnectingDevices() {
        //TODO begin connect the devices
        WifiNetwork wifiNetwork = wifiNetworkArrayList.get(0);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);

        WifiConfiguration wifiConfig = ConnectToApFragment.buildUnsecuredConfig(
                wifiNetwork.getSsid(), false);
        softAPConfigRemover.onSoftApConfigured(wifiConfig.SSID);
        currentSSID = wifiNetwork.getSsid();
        connectToAP(wifiConfig,CONNECT_TO_DEVICE_TIMEOUT_MILLIS);

    }


    public void setPassword(String password) {
        this.password = password;
    }

    private void clearState() {
        if (onTimeoutRunnable != null) {
            mainThreadHandler.removeCallbacks(onTimeoutRunnable);
            onTimeoutRunnable = null;
        }

        if (wifiStateChangeListener != null) {
            appContext.unregisterReceiver(wifiStateChangeListener);
            wifiStateChangeListener = null;
        }

        for (Runnable runnable : setupRunnables) {
            mainThreadHandler.removeCallbacks(runnable);
        }
        setupRunnables.clear();
    }

    public String connectToAP(final WifiConfiguration config, long timeoutInMillis) {
        // cancel any currently running timeout, etc
        clearState();

        final WifiInfo currentConnectionInfo = wifiManager.getConnectionInfo();
        // are we already connected to the right AP?  (this could happen on retries)
        if (isAlreadyConnectedToTargetNetwork(currentConnectionInfo, config.SSID)) {
            // we're already connected to this AP, nothing to do.
            onApConnectionSuccessful(config);
            return null;
        }

        scheduleTimeoutCheck(timeoutInMillis, config);
        wifiStateChangeListener = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                onWifiChangeBroadcastReceived(intent, config);
            }
        };

        appContext.registerReceiver(wifiStateChangeListener,
                new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        final boolean useMoreComplexConnectionProcess = Build.VERSION.SDK_INT < 18;


        // we don't need this for its atomicity, we just need it as a 'final' reference to an
        // integer which can be shared by a couple of the Runnables below
        final AtomicInteger networkID = new AtomicInteger(-1);

        // everything below is created in Runnables and scheduled on the runloop to avoid some
        // wonkiness I ran into when trying to do every one of these steps one right after
        // the other on the same thread.

        final int alreadyConfiguredId = WiFi.getConfiguredNetworkId(config.SSID, getActivity());
        if (alreadyConfiguredId != -1 && !useMoreComplexConnectionProcess) {
            // For some unexplained (and probably sad-trombone-y) reason, if the AP specified was
            // already configured and had been connected to in the past, it will often get to
            // the "CONNECTING" event, but just before firing the "CONNECTED" event, the
            // WifiManager appears to change its mind and reconnects to whatever configured and
            // available AP it feels like.
            //
            // As a remedy, we pre-emptively remove that config.  *shakes fist toward Mountain View*

            setupRunnables.add(new Runnable() {
                @Override
                public void run() {
                    if (wifiManager.removeNetwork(alreadyConfiguredId)) {
                        log.d("Removed already-configured " + config.SSID + " network successfully");
                    } else {
                        log.e("Somehow failed to remove the already-configured network!?");
                        // not calling this state an actual failure, since it might succeed anyhow,
                        // and if it doesn't, the worst case is a longer wait to find that out.
                    }
                }
            });
        }

        if (alreadyConfiguredId == -1 || !useMoreComplexConnectionProcess) {
            setupRunnables.add(new Runnable() {
                @Override
                public void run() {
                    log.d("Adding network " + config.SSID);
                    networkID.set(wifiManager.addNetwork(config));
                    if (networkID.get() == -1) {
                        log.e("Adding network " + config.SSID + " failed.");
                        onApConnectionFailed(config);

                    } else {
                        log.i("Added network with ID " + networkID + " successfully");
                    }
                }
            });
        }

        if (useMoreComplexConnectionProcess) {
            setupRunnables.add(new Runnable() {
                @Override
                public void run() {
                    log.d("Disconnecting from networks; reconnecting momentarily.");
                    wifiManager.disconnect();
                }
            });
        }

        setupRunnables.add(new Runnable() {
            @Override
            public void run() {
                log.i("Enabling network " + config.SSID + " with network ID " + networkID.get());
                wifiManager.enableNetwork(networkID.get(),
                        !useMoreComplexConnectionProcess);
            }
        });
        if (useMoreComplexConnectionProcess) {
            setupRunnables.add(new Runnable() {
                @Override
                public void run() {
                    log.d("Disconnecting from networks; reconnecting momentarily.");
                    wifiManager.reconnect();
                }
            });
        }

        String currentlyConnectedSSID = WiFi.getCurrentlyConnectedSSID(getActivity());
        softAPConfigRemover.onWifiNetworkDisabled(currentlyConnectedSSID);

        long timeout = 0;
        for (Runnable runnable : setupRunnables) {
            EZ.runOnMainThreadDelayed(timeout, runnable);
            timeout += 1500;
        }

        return currentConnectionInfo.getSSID();
    }

    private void onApConnectionFailed(WifiConfiguration config) {

        if (!canStartProcessAgain()) {
            onMaxAttemptsReached();
        } else {
            connectToAP(config,CONNECT_TO_DEVICE_TIMEOUT_MILLIS);
        }

    }

    private void onApConnectionSuccessful(WifiConfiguration config) {

        startConnectWorker();
    }

    private class WifiStateChangeLogger extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            log.d("Received " + WifiManager.NETWORK_STATE_CHANGED_ACTION);
            log.d("EXTRA_NETWORK_INFO: " + intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO));
            // this will only be present if the new state is CONNECTED
            WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
            log.d("WIFI_INFO: " + wifiInfo);
        }

        IntentFilter buildIntentFilter() {
            return new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        }
    }

    private static boolean isAlreadyConnectedToTargetNetwork(WifiInfo currentConnectionInfo,
                                                             String targetNetworkSsid) {
        return (isCurrentlyConnectedToAWifiNetwork(currentConnectionInfo)
                && targetNetworkSsid.equals(currentConnectionInfo.getSSID())
        );
    }

    private static boolean isCurrentlyConnectedToAWifiNetwork(WifiInfo currentConnectionInfo) {
        return (currentConnectionInfo != null
                && truthy(currentConnectionInfo.getSSID())
                && currentConnectionInfo.getNetworkId() != -1
                // yes, this happens.  Thanks, Android.
                && !"0x".equals(currentConnectionInfo.getSSID()));
    }

    private void scheduleTimeoutCheck(long timeoutInMillis, final WifiConfiguration config) {
        onTimeoutRunnable = new Runnable() {

            @Override
            public void run() {
                onApConnectionFailed(config);
            }
        };
        mainThreadHandler.postDelayed(onTimeoutRunnable, timeoutInMillis);
    }

    private void onWifiChangeBroadcastReceived(Intent intent, WifiConfiguration config) {
        // this will only be present if the new state is CONNECTED
        WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
        if (wifiInfo == null || wifiInfo.getSSID() == null) {
            // no WifiInfo or SSID means we're not interested.
            return;
        }
        log.i("Connected to: " + wifiInfo.getSSID());
        String ssid = wifiInfo.getSSID();
        if (ssid.equals(config.SSID) || WiFi.enQuotifySsid(ssid).equals(config.SSID)) {
            // FIXME: find a way to record success in memory in case this happens to happen
            // during a config change (etc)?
            onApConnectionSuccessful(config);
        }
    }

    private void startConnectWorker() {
        // first, make sure we haven't actually been called twice...
        if (connectToApTask != null) {
            log.d("Already running connect worker " + connectToApTask + ", refusing to start another");
            return;
        }

        // FIXME: verify first that we're still connected to the intended network
        if (!canStartProcessAgain()) {
            progressBar.setVisibility(View.INVISIBLE);
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
                    // no exceptions thrown, huzzah
                    progressBar.setVisibility(View.INVISIBLE);
                    //TODO PASS WIFI DETAILS
                    //TODO COMPLETE SETUP
                    //TODO MOVE TO NEXT ITEM AND MORK SUCCESS
                    Toast.makeText(getContext(),"SUCCESS",Toast.LENGTH_SHORT).show();
                    log.d("SUCCESS");
                    //TODO RESET ALL ATTEMPTS VARIABLES
//                    startActivity(new Intent(DiscoverDeviceActivity.this, SelectNetworkActivity.class));
//                    finish();
                    progressBar.setVisibility(View.INVISIBLE);
                    passNetworkDetailsToAP();

                } else if (error instanceof DeviceAlreadyClaimed) {
                    Toast.makeText(getContext(),"Device claimed",Toast.LENGTH_SHORT).show();
                    log.d("Device claimed");
                    progressBar.setVisibility(View.INVISIBLE);
                    onDeviceClaimedByOtherUser();

                } else {
                    // nope, do it all over again.
                    // FIXME: this might be a good time to display some feedback...
                    progressBar.setVisibility(View.INVISIBLE);
                    startConnectWorker();
                    log.d("Try again to connect to photon");
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void passNetworkDetailsToAP() {


         networkToConnectTo = applicationController.selectedNetwork.scan;
        String password = this.password;
        if(password == null){
            //unsecured setup
        }else{
            //secure setup
        }

        publicKey = DeviceSetupState.publicKey;
        sparkCloud = ParticleCloudSDK.getCloud();
        deviceId = DeviceSetupState.deviceToBeSetUpId;
        needToClaimDevice = DeviceSetupState.deviceNeedsToBeClaimed;

        deviceSoftApSsid = currentSSID;
        networkSecretPlaintext = password;


        client = CommandClient.newClientUsingDefaultSocketAddress();

        log.d("Connecting to " + networkToConnectTo + ", with networkSecretPlaintext of size: "
                + ((networkSecretPlaintext == null) ? 0 : networkSecretPlaintext.length()));



        tvCurrentDevice.setText(Phrase.from(getContext(), io.particle.android.sdk.devicesetup.R.string.connecting_text)
                .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format() + " " +
                networkToConnectTo.ssid
        );


        connectingProcessWorkerTask = new ConnectingProcessWorkerTask(buildSteps(), 15);
        connectingProcessWorkerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }



    private boolean canStartProcessAgain() {
        return discoverProcessAttempts < MAX_NUM_DISCOVER_PROCESS_ATTEMPTS;
    }

    private void onMaxAttemptsReached() {
        if (!isResumed) {
            //TODO MOVE TO NEXT ITEM
            getActivity().finish();
            return;
        }

        String errorMsg = Phrase.from(getContext(), io.particle.android.sdk.devicesetup.R.string.unable_to_connect_to_soft_ap)
                .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format().toString();

        new AlertDialog.Builder(getContext())
                .setTitle(io.particle.android.sdk.devicesetup.R.string.error)
                .setMessage(errorMsg)
                .setPositiveButton(io.particle.android.sdk.devicesetup.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        //TODO MOVE TO NEXT ITEM AND MARK FAILED
//                        startActivity(new Intent(DiscoverDeviceActivity.this, GetReadyActivity.class));
//                        finish();
                    }
                })
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        isResumed = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        isResumed = false;
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


                        startConnectWorker();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        //TODO MOVE TO NEXT ITEM
//                        startActivity(new Intent(DiscoverDeviceActivity.this, GetReadyActivity.class));
//                        finish();
                        progressBar.setVisibility(View.INVISIBLE);
                    }
                })
                .show();
    }

    private void resetWorker() {
        discoverProcessWorker = new DiscoverProcessWorker(
                CommandClient.newClientUsingDefaultSocketAddress());
    }

    class ConnectingProcessWorkerTask extends SetupStepsRunnerTask {

        ConnectingProcessWorkerTask(List<SetupStep> steps, int max) {
            super(steps, max);
        }

        @Override
        protected void onProgressUpdate(StepProgress... values) {
            for (StepProgress progress : values) {
                //TODO show progress
            }
        }

        @Override
        protected void onPostExecute(SetupProcessException error) {
            int resultCode;
            if (error == null) {
                log.i("HUZZAH, VICTORY!");
                // FIXME: handle "success, no ownership" case
                resultCode = SuccessActivity.RESULT_SUCCESS;
                progressBar.setVisibility(View.INVISIBLE);
                EZ.runAsync(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Set<String> names = set();
                            for (ParticleDevice device : sparkCloud.getDevices()) {
                                if (device != null && device.getName() != null) {
                                    names.add(device.getName());
                                }
                            }
                            ParticleDevice device = sparkCloud.getDevice(deviceId);
                            if (device != null && !truthy(device.getName())) {
                                device.setName(CoreNameGenerator.generateUniqueName(names));
                            }
                        } catch (ParticleCloudException e) {
                            // FIXME: do real error handling here
                            e.printStackTrace();
                        } catch (Exception e) {
                            // FIXME: remove.
                            e.printStackTrace();
                        }
                    }
                });
                your_array_list.add("Device: " + currentSSID + " >> " + "Connected");
            } else {
                resultCode = error.failedStep.getStepConfig().resultCode;
                your_array_list.add("Device: " + currentSSID + " >> " + "Error");
            }

//            startActivity(SuccessActivity.buildIntent(getContext(), resultCode));
//            finish();

            arrayAdapter.notifyDataSetChanged();
            softAPConfigRemover.reenableWifiNetworks();
            /*wifiNetworkArrayList.remove(0);
            if(!wifiNetworkArrayList.isEmpty()){
                log.i("Connecting next");
                beginConnectingDevices();
            }else{
                log.i("Group connect ended");
            }*/
        }

    }

    private List<SetupStep> buildSteps() {
        ConfigureAPStep configureAPStep = new ConfigureAPStep(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_CONFIGURE_AP)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_CONFIGURE)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.configure_device_wifi_credentials)
                        .build(),
                client, networkToConnectTo, networkSecretPlaintext, publicKey, getContext());

        ConnectDeviceToNetworkStep connectDeviceToNetworkStep = new ConnectDeviceToNetworkStep(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_CONNECT_AP)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_CONFIGURE)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.connect_to_wifi_network)
                        .build(),
                client, getContext());

        WaitForDisconnectionFromDeviceStep waitForDisconnectionFromDeviceStep = new WaitForDisconnectionFromDeviceStep(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_DISCONNECT_FROM_DEVICE)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_NO_DISCONNECT)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.connect_to_wifi_network)
                        .build(),
                deviceSoftApSsid, getContext());

        EnsureSoftApNotVisible ensureSoftApNotVisible = new EnsureSoftApNotVisible(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_DISCONNECT_FROM_DEVICE)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_CONFIGURE)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.wait_for_device_cloud_connection)
                        .build(),
                deviceSoftApSsid, getContext());

        WaitForCloudConnectivityStep waitForLocalCloudConnectivityStep = new WaitForCloudConnectivityStep(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_DISCONNECT_FROM_DEVICE)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_NO_DISCONNECT)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.check_for_internet_connectivity)
                        .build(),
                sparkCloud, getActivity().getApplicationContext());

        CheckIfDeviceClaimedStep checkIfDeviceClaimedStep = new CheckIfDeviceClaimedStep(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_CLAIM)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_CLAIMING)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.verify_product_ownership)
                        .build(),
                sparkCloud, deviceId, needToClaimDevice);

        return list(
                configureAPStep,
                connectDeviceToNetworkStep,
                waitForDisconnectionFromDeviceStep,
                ensureSoftApNotVisible,
                waitForLocalCloudConnectivityStep,
                checkIfDeviceClaimedStep
        );
    }

    private void onWifiDisabled() {
        log.d("Wi-Fi disabled; prompting user");
        new AlertDialog.Builder(getContext())
                .setTitle(io.particle.android.sdk.devicesetup.R.string.wifi_required)
                .setPositiveButton(io.particle.android.sdk.devicesetup.R.string.enable_wifi, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        log.i("Enabling Wi-Fi at the user's request.");
                        wifiManager.setWifiEnabled(true);
                    }
                })
                .setNegativeButton(io.particle.android.sdk.devicesetup.R.string.exit_setup, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        getActivity().finish();
                    }
                })
                .show();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getActivity().isFinishing() && connectingProcessWorkerTask != null &&
                !connectingProcessWorkerTask.isCancelled()) {
            connectingProcessWorkerTask.cancel(true);
            connectingProcessWorkerTask = null;
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        softAPConfigRemover.removeAllSoftApConfigs();
        softAPConfigRemover.reenableWifiNetworks();
    }
}
