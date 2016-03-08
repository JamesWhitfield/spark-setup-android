package io.particle.devicesetup.exampleapp;

import android.app.ProgressDialog;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.phrase.Phrase;

import java.util.concurrent.TimeUnit;

import io.particle.android.sdk.devicesetup.ui.ConnectToApFragment;
import io.particle.android.sdk.utils.SoftAPConfigRemover;

/**
 * Created by jwhit on 08/03/2016.
 */
//TODO for each device
    //TODO connect over wifi
    //TODO send item
public class ConnectingGroupFragment extends Fragment implements SetupGroupActivity.ConnectingInterface{



    public void setPassword(String password) {
        this.password = password;
    }

    String password;
    private SoftAPConfigRemover softAPConfigRemover;
    private int discoverProcessAttempts = 0;
    private static final long CONNECT_TO_DEVICE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private ProgressDialog connectToApSpinnerDialog;

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


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_group_connect, container, false);

        ApplicationController applicationController = (ApplicationController) getActivity().getApplication();
        Log.i("Hello","Connecting:" + applicationController.photonSetupGroup.size());
        Log.i("Hello","To:" + applicationController.selectedNetwork.getSsid());
        return view;
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
    public void onApConnectionSuccessful() {
        startConnectWorker();
    }

    @Override
    public void onApConnectionFailed() {
        hideProgressDialog();

        if (!canStartProcessAgain()) {
            onMaxAttemptsReached();
        } else {
            connectToSoftAp(config);
        }
    }
}
