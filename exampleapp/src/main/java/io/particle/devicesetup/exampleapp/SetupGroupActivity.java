package io.particle.devicesetup.exampleapp;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;

import io.particle.android.sdk.devicesetup.ui.ConnectingActivity;
import io.particle.android.sdk.devicesetup.ui.PasswordEntryActivity;
import io.particle.android.sdk.utils.WiFi;

/**
 * Created by jwhit on 08/03/2016.
 */
public class SetupGroupActivity extends AppCompatActivity implements PasswordEntryFragment.SetNetworkPassword{

    private static final String PASSWORD_FRAMENT_TAG = "password";
    ApplicationController applicationController;
    FragmentManager fragmentManager;
    Fragment passwordFragment;
    String password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_group);
        applicationController = (ApplicationController)getApplication();


        String softApSSID = WiFi.getCurrentlyConnectedSSID(this);

        //TODO if target network is not secure go striaght to settingup devices
        //TODO else get paassword for target newtwork first
        if (applicationController.selectedNetwork.isSecured()) {
            //add password fragment and set password
//            startActivity(PasswordEntryActivity.buildIntent(this, selectedNetwork.scan));

            if (savedInstanceState != null) {
                passwordFragment = getSupportFragmentManager().findFragmentByTag(PASSWORD_FRAMENT_TAG);
            } else {

                fragmentManager = getSupportFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                passwordFragment = PasswordEntryFragment.newInstance();
                fragmentTransaction.add(R.id.fragmentContainer, passwordFragment, PASSWORD_FRAMENT_TAG);
                fragmentTransaction.commit();
            }

        } else {
            //begin connecting setup
//            startActivity(ConnectingActivity.buildIntent(this, softApSSID, selectedNetwork.scan));
        }


    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public void replaceFragment() {


        Fragment newFragment = ConnectingGroupFragment.newInstance(password);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, newFragment);
        transaction.addToBackStack(null);
        transaction.commit();

        //TODO replace password fragment with group connect fragment
        //TODO create group create frament
        //TODO connect to each device one at a time and pass target wifi details through
        startActivity(ConnectingActivity.buildIntent(this,
                WiFi.getCurrentlyConnectedSSID(this),
                networkToConnectTo,
                secret));


    }
}
