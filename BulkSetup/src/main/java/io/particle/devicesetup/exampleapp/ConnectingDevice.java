package io.particle.devicesetup.exampleapp;

import android.content.Context;
import android.os.AsyncTask;
import android.view.View;

import com.squareup.phrase.Phrase;

import java.security.PublicKey;
import java.util.List;
import java.util.Set;

import io.particle.android.sdk.cloud.ParticleCloud;
import io.particle.android.sdk.cloud.ParticleCloudSDK;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.devicesetup.SetupProcessException;
import io.particle.android.sdk.devicesetup.commands.CommandClient;
import io.particle.android.sdk.devicesetup.commands.ScanApCommand;
import io.particle.android.sdk.devicesetup.setupsteps.CheckIfDeviceClaimedStep;
import io.particle.android.sdk.devicesetup.setupsteps.ConfigureAPStep;
import io.particle.android.sdk.devicesetup.setupsteps.ConnectDeviceToNetworkStep;
import io.particle.android.sdk.devicesetup.setupsteps.EnsureSoftApNotVisible;
import io.particle.android.sdk.devicesetup.setupsteps.SetupStep;
import io.particle.android.sdk.devicesetup.setupsteps.SetupStepsRunnerTask;
import io.particle.android.sdk.devicesetup.setupsteps.StepConfig;
import io.particle.android.sdk.devicesetup.setupsteps.StepProgress;
import io.particle.android.sdk.devicesetup.setupsteps.WaitForCloudConnectivityStep;
import io.particle.android.sdk.devicesetup.setupsteps.WaitForDisconnectionFromDeviceStep;
import io.particle.android.sdk.devicesetup.ui.DeviceSetupState;
import io.particle.android.sdk.devicesetup.ui.SuccessActivity;
import io.particle.android.sdk.utils.CoreNameGenerator;
import io.particle.android.sdk.utils.EZ;
import io.particle.android.sdk.utils.SoftAPConfigRemover;
import io.particle.android.sdk.utils.TLog;

import static io.particle.android.sdk.utils.Py.list;
import static io.particle.android.sdk.utils.Py.set;
import static io.particle.android.sdk.utils.Py.truthy;

/**
 * Created by jwhit on 10/03/2016.
 */
public class ConnectingDevice {



    private static final int MAX_RETRIES_CONFIGURE_AP = 5;
    private static final int MAX_RETRIES_CONNECT_AP = 5;
    private static final int MAX_RETRIES_DISCONNECT_FROM_DEVICE = 5;
    private static final int MAX_RETRIES_CLAIM = 5;

    private CommandClient client;
    private ConnectingProcessWorkerTask connectingProcessWorkerTask;
    private SoftAPConfigRemover softAPConfigRemover;

    private ScanApCommand.Scan networkToConnectTo;
    private String networkSecretPlaintext;
    private PublicKey publicKey;
    private String deviceSoftApSsid;
    private ParticleCloud sparkCloud;
    private String deviceId;
    private boolean needToClaimDevice;


    private Context context;
    AcitvityListener acitvityListener;

    private static final TLog log = TLog.get(ConnectingDevice.class);

    public static ConnectingDevice getInstance(Context context,AcitvityListener acitvityListener, ScanApCommand.Scan networkToConnectTo,String deviceSoftApSsid,String password){

        ConnectingDevice connectingDevice = new ConnectingDevice(context,acitvityListener,networkToConnectTo,deviceSoftApSsid, password);

        return connectingDevice;
    }

    public interface AcitvityListener{

        void setConnectingText(String device_name);

        void deviceSetupComplete(int resultCode);
    }

    public ConnectingDevice(Context context, AcitvityListener acitvityListener, ScanApCommand.Scan networkToConnectTo, String deviceSoftApSsid, String networkSecretPlaintext){

        softAPConfigRemover = new SoftAPConfigRemover(context);

        publicKey = DeviceSetupState.publicKey;
        sparkCloud = ParticleCloudSDK.getCloud();
        deviceId = DeviceSetupState.deviceToBeSetUpId;
        needToClaimDevice = DeviceSetupState.deviceNeedsToBeClaimed;

        this.deviceSoftApSsid = deviceSoftApSsid;
        this.networkToConnectTo = networkToConnectTo;
        this.networkSecretPlaintext = networkSecretPlaintext;
        this.acitvityListener = acitvityListener;
        this.context = context;

        client = CommandClient.newClientUsingDefaultSocketAddress();

    }

    public void begin(){

        log.d("Connecting to " + this.networkToConnectTo + ", with networkSecretPlaintext of size: "
                + ((networkSecretPlaintext == null) ? 0 : networkSecretPlaintext.length()));

        //TODO show config in fragment
        //TODO Setup cancel button in fragment
        /*Button cancelButton = Ui.findView(this, io.particle.android.sdk.devicesetup.R.id.action_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectingProcessWorkerTask.cancel(false);
                finish();
            }
        });*/
        //TODO show connecting text
        acitvityListener.setConnectingText(Phrase.from(context, io.particle.android.sdk.devicesetup.R.string.connecting_text)
                .put("device_name", context.getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format().toString() + "Wifi");


        connectingProcessWorkerTask = new ConnectingProcessWorkerTask(buildSteps(), 15);
        connectingProcessWorkerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void onCancel(){

        connectingProcessWorkerTask.cancel(false);
    }

    protected void onDestroy() {
        softAPConfigRemover.removeAllSoftApConfigs();
        softAPConfigRemover.reenableWifiNetworks();
        onStop(true);
    }

    protected void onStop(boolean finishing) {
        if (finishing && connectingProcessWorkerTask != null &&
                !connectingProcessWorkerTask.isCancelled()) {
            connectingProcessWorkerTask.cancel(true);
            connectingProcessWorkerTask = null;
        }
    }

    class ConnectingProcessWorkerTask extends SetupStepsRunnerTask {

        ConnectingProcessWorkerTask(List<SetupStep> steps, int max) {
            super(steps, max);
        }

        @Override
        protected void onProgressUpdate(StepProgress... values) {
            int i = 0;
            for (StepProgress progress : values) {
                //TODO show progress somehow
                /*View v = findViewById(progress.stepId);
                if (v != null) {
                    updateProgress(progress, v);
                }*/
                /*if(progress.status == 2){
                    i++;
                }*/
            }
//            acitvityListener.setConnectingText("Photo Wifi setup stage: " + i + "/"+values.length);
        }

        @Override
        protected void onPostExecute(SetupProcessException error) {
            int resultCode;
            if (error == null) {
                log.i("HUZZAH, VICTORY!");
                // FIXME: handle "success, no ownership" case
                resultCode = SuccessActivity.RESULT_SUCCESS;

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
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                resultCode = error.failedStep.getStepConfig().resultCode;
            }

            //TODO What to do when finished
            acitvityListener.deviceSetupComplete(resultCode);
//            startActivity(SuccessActivity.buildIntent(ConnectingActivity.this, resultCode));
//            finish();
        }

        private void updateProgress(StepProgress progress, View progressStepContainer) {
            //TODO Show progress in connect somehow
            /*ProgressBar progBar = Ui.findView(progressStepContainer, io.particle.android.sdk.devicesetup.R.id.spinner);
            ImageView checkmark = Ui.findView(progressStepContainer, io.particle.android.sdk.devicesetup.R.id.checkbox);

            // don't show the spinner again if we've already shown the checkmark,
            // regardless of the underlying state that might hide
            if (checkmark.getVisibility() == View.VISIBLE) {
                return;
            }

            progressStepContainer.setVisibility(View.VISIBLE);

            if (progress.status == StepProgress.STARTING) {
                checkmark.setVisibility(View.GONE);

                progBar.setProgressDrawable(tintedSpinner);
                progBar.setVisibility(View.VISIBLE);

            } else {
                progBar.setVisibility(View.GONE);

                checkmark.setImageDrawable(tintedCheckmark);
                checkmark.setVisibility(View.VISIBLE);
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
                client, networkToConnectTo, networkSecretPlaintext, publicKey, context);

        ConnectDeviceToNetworkStep connectDeviceToNetworkStep = new ConnectDeviceToNetworkStep(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_CONNECT_AP)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_CONFIGURE)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.connect_to_wifi_network)
                        .build(),
                client, context);

        WaitForDisconnectionFromDeviceStep waitForDisconnectionFromDeviceStep = new WaitForDisconnectionFromDeviceStep(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_DISCONNECT_FROM_DEVICE)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_NO_DISCONNECT)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.connect_to_wifi_network)
                        .build(),
                deviceSoftApSsid, context);

        EnsureSoftApNotVisible ensureSoftApNotVisible = new EnsureSoftApNotVisible(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_DISCONNECT_FROM_DEVICE)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_CONFIGURE)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.wait_for_device_cloud_connection)
                        .build(),
                deviceSoftApSsid, context);

        WaitForCloudConnectivityStep waitForLocalCloudConnectivityStep = new WaitForCloudConnectivityStep(
                StepConfig.newBuilder()
                        .setMaxAttempts(MAX_RETRIES_DISCONNECT_FROM_DEVICE)
                        .setResultCode(SuccessActivity.RESULT_FAILURE_NO_DISCONNECT)
                        .setStepId(io.particle.android.sdk.devicesetup.R.id.check_for_internet_connectivity)
                        .build(),
                sparkCloud,context);

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
}
