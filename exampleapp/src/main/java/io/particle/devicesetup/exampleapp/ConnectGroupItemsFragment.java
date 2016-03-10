package io.particle.devicesetup.exampleapp;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.phrase.Phrase;

import java.util.ArrayList;
import java.util.List;

import io.particle.android.sdk.devicesetup.commands.ScanApCommand;
import io.particle.android.sdk.devicesetup.model.WifiNetwork;
import io.particle.android.sdk.devicesetup.ui.ConnectToApFragment;
import io.particle.android.sdk.utils.TLog;
import io.particle.android.sdk.utils.ui.Ui;
import me.zhanghai.android.materialprogressbar.IndeterminateHorizontalProgressDrawable;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

/**
 * Created by jwhit on 10/03/2016.
 */
public class ConnectGroupItemsFragment extends Fragment implements ConnectToPhotonManager.FragmentPassBack, ConnectingDevice.AcitvityListener {

    private ArrayList<WifiNetwork> wifiNetworkArrayList;
    ApplicationController applicationController;

    private String networkSecretPlaintext;
    private Button btnCancel;
    private TextView tvCurrentDevice;
    private ScanApCommand.Scan networkToConnectTo;
    private ListView lvResults;
    List<String> your_array_list;
    private ArrayAdapter<String> arrayAdapter;
    private String password;
    private WifiManager wifiManager;
    private static final TLog log = TLog.get(ConnectGroupItemsFragment.class);

    private ConnectToPhotonManager connectToPhotonManager;
    private ConnectingDevice connectingDevice;
    private ProgressBar progressBar;

    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_SUCCESS_UNKNOWN_OWNERSHIP = 2;
    public static final int RESULT_FAILURE_CLAIMING = 3;
    public static final int RESULT_FAILURE_CONFIGURE = 4;
    public static final int RESULT_FAILURE_NO_DISCONNECT = 5;
    public static final int RESULT_FAILURE_LOST_CONNECTION_TO_DEVICE = 6;

    public static ConnectGroupItemsFragment newInstance() {

        ConnectGroupItemsFragment connectingGroupFragment = new ConnectGroupItemsFragment();
        return connectingGroupFragment;
    }

    public static ConnectGroupItemsFragment newInstance(String password) {

        ConnectGroupItemsFragment connectingGroupFragment = new ConnectGroupItemsFragment();
        connectingGroupFragment.setPassword(password);

        return connectingGroupFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applicationController = (ApplicationController) getActivity().getApplication();
        wifiNetworkArrayList = new ArrayList<WifiNetwork>();
        wifiNetworkArrayList.addAll(applicationController.photonSetupGroup);
        wifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        networkToConnectTo = applicationController.selectedNetwork.scan;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_group_connect, container, false);

        Log.i("Hello","Connecting:" + applicationController.photonSetupGroup.size());
        Log.i("Hello","To:" + applicationController.selectedNetwork.getSsid());

        TextView tvConfig = (TextView) view.findViewById(R.id.tvconfig);
        progressBar = (ProgressBar) view.findViewById(R.id.pbProgress);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.GREEN));
        }else{
            progressBar.getIndeterminateDrawable().setColorFilter(
                    Color.GREEN, PorterDuff.Mode.MULTIPLY);
        }
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
                /*if(connectingProcessWorkerTask != null) {
                    connectingProcessWorkerTask.cancel(false);
                }
                getActivity().finish();*/
                cleanUp();
                getActivity().finish();
            }
        });

        if (!wifiManager.isWifiEnabled()) {
            onWifiDisabled();
        }else{

            beginConnectingDevices();
        }

        return view;
    }

    @Override
    public void showProgress() {

        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideProgress() {

        progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void setItemMessage(String errorMsg) {
        tvCurrentDevice.setText(errorMsg);
    }

    @Override
    public void photonConnected(String currentSSID) {
        connectingDevice = ConnectingDevice.getInstance(getContext(),this,networkToConnectTo,currentSSID,password);
        connectingDevice.begin();
        tvCurrentDevice.setText("Photon connected");
    }

    @Override
    public void moveToNext() {
        //TODO clear and stop all processes, get new objects
        cleanUp();
        wifiNetworkArrayList.remove(0);

        if(!wifiNetworkArrayList.isEmpty()){

            beginConnectingDevices();
        }else{
            tvCurrentDevice.setText("");
        }
    }

    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanUp();
    }

    private void beginConnectingDevices() {

        WifiNetwork wifiNetwork = wifiNetworkArrayList.get(0);


        connectToPhotonManager = ConnectToPhotonManager.getInstance(getContext(),this,wifiNetwork.getSsid());
        connectToPhotonManager.begin();

    }

    public void setPassword(String password) {
        this.password = password;
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
    public void setConnectingText(String device_name) {
        tvCurrentDevice.setText(device_name);
    }

    @Override
    public void deviceSetupComplete(int resultCode) {
        hideProgress();

        Pair<? extends CharSequence, CharSequence> resultStrings = buildUiStringPair(resultCode);
        your_array_list.add(resultStrings.first + "\n" + resultStrings.second);
        arrayAdapter.notifyDataSetChanged();

        moveToNext();

    }

    private void cleanUp(){
        tvCurrentDevice.setText("Cleaning up old processes");
        log.d("Cleaning up old processes");
        if(connectToPhotonManager != null){

            connectToPhotonManager.destroy();
        }
        if(connectingDevice != null){

            connectingDevice.onDestroy();
        }


    }

    private Pair<? extends CharSequence, CharSequence> buildUiStringPair(int resultCode) {
        Pair<Integer, Integer> stringIds = resultCodesToStringIds.get(resultCode);
        return Pair.create(getString(stringIds.first),
                Phrase.from(getContext(), stringIds.second)
                        .put("device_name", wifiNetworkArrayList.get(0).getSsid())
                        .format());
    }

    private static final SparseArray<Pair<Integer, Integer>> resultCodesToStringIds;

    static {
        resultCodesToStringIds = new SparseArray<>(6);
        resultCodesToStringIds.put(RESULT_SUCCESS, Pair.create(
                io.particle.android.sdk.devicesetup.R.string.setup_success_summary,
                io.particle.android.sdk.devicesetup.R.string.setup_success_details));

        resultCodesToStringIds.put(RESULT_SUCCESS_UNKNOWN_OWNERSHIP, Pair.create(
                io.particle.android.sdk.devicesetup.R.string.setup_success_unknown_ownership_summary,
                io.particle.android.sdk.devicesetup.R.string.setup_success_unknown_ownership_details));

        resultCodesToStringIds.put(RESULT_FAILURE_CLAIMING, Pair.create(
                io.particle.android.sdk.devicesetup.R.string.setup_failure_claiming_summary,
                io.particle.android.sdk.devicesetup.R.string.setup_failure_claiming_details));

        resultCodesToStringIds.put(RESULT_FAILURE_CONFIGURE, Pair.create(
                io.particle.android.sdk.devicesetup.R.string.setup_failure_configure_summary,
                io.particle.android.sdk.devicesetup.R.string.setup_failure_configure_details));

        resultCodesToStringIds.put(RESULT_FAILURE_NO_DISCONNECT, Pair.create(
                io.particle.android.sdk.devicesetup.R.string.setup_failure_no_disconnect_from_device_summary,
                io.particle.android.sdk.devicesetup.R.string.setup_failure_no_disconnect_from_device_details));

        resultCodesToStringIds.put(RESULT_FAILURE_LOST_CONNECTION_TO_DEVICE, Pair.create(
                io.particle.android.sdk.devicesetup.R.string.setup_failure_configure_summary,
                io.particle.android.sdk.devicesetup.R.string.setup_failure_lost_connection_to_device));
    }
}
