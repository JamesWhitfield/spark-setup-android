package io.particle.devicesetup.exampleapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import io.particle.android.sdk.devicesetup.ParticleDeviceSetupLibrary;
import io.particle.android.sdk.utils.ui.Ui;

public class MainActivity extends AppCompatActivity {

    public static final String MODE_STANDARD = "STANDARD";
    public static final String MODE_GROUP = "GROUP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //This activity allows the user to choose between standard and group setup options
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ParticleDeviceSetupLibrary.init(this.getApplicationContext(), MainActivity.class);

        Ui.findView(this, R.id.start_setup_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSetupMode(MODE_STANDARD);
                invokeDeviceSetup();
            }
        });

        Ui.findView(this, R.id.start__group_setup_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSetupMode(MODE_GROUP);
                invokeGroupDeviceSetup();
            }
        });

    }

    private void setSetupMode(String mode) {

        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.setup_preferences), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.setup_mode),mode);
        editor.commit();

    }

    public void invokeDeviceSetup() {
        ParticleDeviceSetupLibrary.startDeviceSetup(this);
    }
    public void invokeGroupDeviceSetup() {
        startActivity(new Intent(this, GetGroupReadyActivity.class));
    }
}
