package io.particle.devicesetup.exampleapp;

import android.content.Intent;

import io.particle.android.sdk.devicesetup.model.ScanAPCommandResult;
import io.particle.android.sdk.devicesetup.ui.ConnectingActivity;
import io.particle.android.sdk.devicesetup.ui.PasswordEntryActivity;
import io.particle.android.sdk.devicesetup.ui.SelectNetworkActivity;
import io.particle.android.sdk.utils.WiFi;

/**
 * Created by jwhit on 08/03/2016.
 */
public class SelectNetworkForGroupActivity extends SelectNetworkActivity {

    @Override
    public void onNetworkSelected(ScanAPCommandResult selectedNetwork) {
        wifiListFragment.stopAggroLoading();

        ((ApplicationController)getApplication()).selectedNetwork = selectedNetwork;

        startActivity(new Intent(this,SetupGroupActivity.class));

        finish();
    }
}
