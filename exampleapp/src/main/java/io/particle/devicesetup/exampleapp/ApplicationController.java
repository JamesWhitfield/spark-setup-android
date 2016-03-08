package io.particle.devicesetup.exampleapp;

import android.app.Application;

import java.util.ArrayList;

import io.particle.android.sdk.devicesetup.model.ScanAPCommandResult;
import io.particle.android.sdk.devicesetup.model.WifiNetwork;

/**
 * Created by jwhit on 08/03/2016.
 */
public class ApplicationController extends Application{

    public ArrayList<WifiNetwork> photonSetupGroup = new ArrayList<>();
    public ScanAPCommandResult selectedNetwork;
}
