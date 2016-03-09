package io.particle.devicesetup.exampleapp;

import android.content.Intent;
import android.view.View;
import android.widget.CheckBox;

import io.particle.android.sdk.devicesetup.commands.ScanApCommand;
import io.particle.android.sdk.devicesetup.commands.data.WifiSecurity;
import io.particle.android.sdk.devicesetup.model.ScanAPCommandResult;
import io.particle.android.sdk.devicesetup.ui.ConnectingActivity;
import io.particle.android.sdk.devicesetup.ui.ManualNetworkEntryActivity;
import io.particle.android.sdk.devicesetup.ui.PasswordEntryActivity;
import io.particle.android.sdk.utils.WiFi;
import io.particle.android.sdk.utils.ui.Ui;

/**
 * Created by jwhit on 08/03/2016.
 */
public class ManualNetworkEntryGroupActivity extends ManualNetworkEntryActivity {

    @Override
    public void onConnectClicked(View view) {
        String ssid = Ui.getText(this, io.particle.android.sdk.devicesetup.R.id.network_name, true);
        //TODO TESTING
        ssid ="Get in the WAN";
        CheckBox requiresPassword = Ui.findView(this, io.particle.android.sdk.devicesetup.R.id.network_requires_password);
        ScanApCommand.Scan scan;
        if (requiresPassword.isChecked()) {
            scan = new ScanApCommand.Scan(ssid, WifiSecurity.WPA2_AES_PSK.asInt(), 0);
        } else {
            scan = new ScanApCommand.Scan(ssid, WifiSecurity.OPEN.asInt(), 0);
        }
        ((ApplicationController)getApplication()).selectedNetwork = new ScanAPCommandResult(scan);
        startActivity(new Intent(this,SetupGroupActivity.class));
        finish();
    }
}
