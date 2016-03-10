package io.particle.devicesetup.exampleapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.particle.android.sdk.devicesetup.ui.ConnectToApFragment;
import io.particle.android.sdk.utils.EZ;
import io.particle.android.sdk.utils.SoftAPConfigRemover;
import io.particle.android.sdk.utils.TLog;
import io.particle.android.sdk.utils.WiFi;

import static io.particle.android.sdk.utils.Py.list;
import static io.particle.android.sdk.utils.Py.truthy;

/**
 * Created by jwhit on 10/03/2016.
 */
public class ConnectToAp {

    private final WifiConfiguration config;
    private Context context;

    private WifiStateChangeLogger wifiStateChangeLogger;
    private BroadcastReceiver wifiStateChangeListener;
    private WifiManager wifiManager;
    private SoftAPConfigRemover softAPConfigRemover;
    private ClientDecorator client;

    // for handling through the runloop
    private Handler mainThreadHandler;
    private Runnable onTimeoutRunnable;
    private List<Runnable> setupRunnables = list();

    private static final TLog log = TLog.get(ConnectToAp.class);
    private Long timeout;

    public void setClient(ClientDecorator client) {
        this.client = client;
    }

    public interface Client {

        void onApConnectionSuccessful(WifiConfiguration config);

        void onApConnectionFailed(WifiConfiguration config);

    }

    public static ConnectToAp getInstance(Context context, Client client, WifiConfiguration config, long connectToDeviceTimeoutMillis){

        ConnectToAp connectToPhotonManager = new ConnectToAp(context, client,config);
        connectToPhotonManager.setTimeout(connectToDeviceTimeoutMillis);

        return connectToPhotonManager;
    }

    private void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    private ConnectToAp(Context context, Client client, WifiConfiguration config){

        this.context =context;
        this.config = config;
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        softAPConfigRemover = new SoftAPConfigRemover(context);
        mainThreadHandler = new Handler();
        wifiStateChangeLogger = new WifiStateChangeLogger();

        ClientDecorator clientDecorator = new ClientDecorator();
        clientDecorator.setDecoratedClient(client);
        this.setClient(clientDecorator);
    }

    public void begin(){
        context.registerReceiver(wifiStateChangeLogger, wifiStateChangeLogger.buildIntentFilter());
        connectToAP(config,timeout);
    }

    public void destroy(){
        context.unregisterReceiver(wifiStateChangeLogger);
        clearState();
        client.setDecoratedClient(null);
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

    public String connectToAP(final WifiConfiguration config, long timeoutInMillis) {
        // cancel any currently running timeout, etc
        clearState();

        final WifiInfo currentConnectionInfo = wifiManager.getConnectionInfo();
        // are we already connected to the right AP?  (this could happen on retries)
        if (isAlreadyConnectedToTargetNetwork(currentConnectionInfo, config.SSID)) {
            // we're already connected to this AP, nothing to do.
            client.onApConnectionSuccessful(config);
            return null;
        }

        scheduleTimeoutCheck(timeoutInMillis, config);
        wifiStateChangeListener = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                onWifiChangeBroadcastReceived(intent, config);
            }
        };

        context.registerReceiver(wifiStateChangeListener,
                new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        final boolean useMoreComplexConnectionProcess = Build.VERSION.SDK_INT < 18;


        // we don't need this for its atomicity, we just need it as a 'final' reference to an
        // integer which can be shared by a couple of the Runnables below
        final AtomicInteger networkID = new AtomicInteger(-1);

        // everything below is created in Runnables and scheduled on the runloop to avoid some
        // wonkiness I ran into when trying to do every one of these steps one right after
        // the other on the same thread.

        final int alreadyConfiguredId = WiFi.getConfiguredNetworkId(config.SSID, context);
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
                        client.onApConnectionFailed(config);

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

        String currentlyConnectedSSID = WiFi.getCurrentlyConnectedSSID(context);
        softAPConfigRemover.onWifiNetworkDisabled(currentlyConnectedSSID);

        long timeout = 0;
        for (Runnable runnable : setupRunnables) {
            EZ.runOnMainThreadDelayed(timeout, runnable);
            timeout += 1500;
        }

        return currentConnectionInfo.getSSID();
    }

    private static boolean isAlreadyConnectedToTargetNetwork(WifiInfo currentConnectionInfo,
                                                             String targetNetworkSsid) {
        return (isCurrentlyConnectedToAWifiNetwork(currentConnectionInfo)
                && targetNetworkSsid.equals(currentConnectionInfo.getSSID())
        );
    }

    private void clearState() {
        if (onTimeoutRunnable != null) {
            mainThreadHandler.removeCallbacks(onTimeoutRunnable);
            onTimeoutRunnable = null;
        }

        if (wifiStateChangeListener != null) {
            context.unregisterReceiver(wifiStateChangeListener);
            wifiStateChangeListener = null;
        }

        for (Runnable runnable : setupRunnables) {
            mainThreadHandler.removeCallbacks(runnable);
        }
        setupRunnables.clear();
    }

    private void scheduleTimeoutCheck(long timeoutInMillis, final WifiConfiguration config) {
        onTimeoutRunnable = new Runnable() {

            @Override
            public void run() {
                client.onApConnectionFailed(config);
            }
        };
        mainThreadHandler.postDelayed(onTimeoutRunnable, timeoutInMillis);
    }

    private static boolean isCurrentlyConnectedToAWifiNetwork(WifiInfo currentConnectionInfo) {
        return (currentConnectionInfo != null
                && truthy(currentConnectionInfo.getSSID())
                && currentConnectionInfo.getNetworkId() != -1
                // yes, this happens.  Thanks, Android.
                && !"0x".equals(currentConnectionInfo.getSSID()));
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
            client.onApConnectionSuccessful(config);
        }
    }

    private class ClientDecorator implements Client {

        Client decoratedClient;

        @Override
        public void onApConnectionSuccessful(WifiConfiguration config) {
            clearState();
            if (decoratedClient != null) {
                decoratedClient.onApConnectionSuccessful(config);

            }
        }

        @Override
        public void onApConnectionFailed(WifiConfiguration config) {
            clearState();
            if (decoratedClient != null) {
                decoratedClient.onApConnectionFailed(config);
            }
        }

        void setDecoratedClient(Client decoratedClient) {
            this.decoratedClient = decoratedClient;
        }

    }

}
