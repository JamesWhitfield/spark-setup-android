package io.particle.devicesetup.exampleapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;

import io.particle.android.sdk.devicesetup.commands.ScanApCommand;
import io.particle.android.sdk.devicesetup.commands.data.WifiSecurity;
import io.particle.android.sdk.devicesetup.ui.ConnectingActivity;
import io.particle.android.sdk.devicesetup.ui.SelectNetworkActivity;
import io.particle.android.sdk.utils.TLog;
import io.particle.android.sdk.utils.WiFi;
import io.particle.android.sdk.utils.ui.Ui;

/**
 * Created by jwhit on 08/03/2016.
 */
public class PasswordEntryFragment extends Fragment {

    private CheckBox cbShowPwdBox;
    private EditText etPasswordBox;
    private TextView tvSsid,tvSecMsg;
    private Button btnConnect,btnCancel;
    private ScanApCommand.Scan networkToConnectTo;
    private static final TLog log = TLog.get(PasswordEntryFragment.class);
    private SetNetworkPassword mCallback;

    public interface SetNetworkPassword{
        public void setPassword(String password);
        public void replaceFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        networkToConnectTo = ((ApplicationController)getActivity().getApplication()).selectedNetwork.scan;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.activity_password_entry, container, false);


        etPasswordBox = (EditText) view.findViewById( io.particle.android.sdk.devicesetup.R.id.password);
        cbShowPwdBox = (CheckBox) view.findViewById(  io.particle.android.sdk.devicesetup.R.id.show_password);
        tvSsid = (TextView) view.findViewById(io.particle.android.sdk.devicesetup.R.id.ssid);
        tvSecMsg = (TextView) view.findViewById(  io.particle.android.sdk.devicesetup.R.id.security_msg);
        btnCancel = (Button) view.findViewById(io.particle.android.sdk.devicesetup.R.id.action_cancel ) ;
        btnConnect = (Button) view.findViewById(io.particle.android.sdk.devicesetup.R.id.action_connect ) ;

        etPasswordBox.requestFocus();
        initViews();


        return view;

    }

    public static Fragment newInstance() {

        return new PasswordEntryFragment();
    }

    private void initViews() {
        tvSsid.setText(networkToConnectTo.ssid);
        tvSecMsg.setText(getSecurityTypeMsg());

        // set up onClick (et al) listeners
        cbShowPwdBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                togglePasswordVisibility(isChecked);
            }
        });
        // set up initial visibility state
        togglePasswordVisibility(cbShowPwdBox.isChecked());

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onConnectClicked(v);
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCancelClicked(v);
            }
        });
    }

    private void togglePasswordVisibility(boolean showPassword) {
        int inputType;
        if (showPassword) {
            inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        } else {
            inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        }
        etPasswordBox.setInputType(inputType);
    }

    private String getSecurityTypeMsg() {
        WifiSecurity securityType = WifiSecurity.fromInteger(networkToConnectTo.wifiSecurityType);
        // FIXME: turn into string resources
        switch (securityType) {
            case WEP_SHARED:
            case WEP_PSK:
                return "Secured with WEP";
            case WPA_AES_PSK:
            case WPA_TKIP_PSK:
                return "Secured with WPA";
            case WPA2_AES_PSK:
            case WPA2_MIXED_PSK:
            case WPA2_TKIP_PSK:
                return "Secured with WPA2";
        }

        log.e("No security string found for " + securityType + "!");
        return "";
    }

    public void onCancelClicked(View view) {
        getActivity().startActivity(new Intent(getActivity().getApplicationContext(),SelectNetworkForGroupActivity.class));
        getActivity().finish();
    }

    public void onConnectClicked(View view) {
        String secret = etPasswordBox.getText().toString();
        mCallback.setPassword(secret);
        mCallback.replaceFragment();

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mCallback = (SetNetworkPassword) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }
}
