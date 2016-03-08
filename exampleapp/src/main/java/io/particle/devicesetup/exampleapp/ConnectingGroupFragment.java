package io.particle.devicesetup.exampleapp;

import android.support.v4.app.Fragment;

/**
 * Created by jwhit on 08/03/2016.
 */
public class ConnectingGroupFragment extends Fragment{


    public static ConnectingGroupFragment newInstance() {

        ConnectingGroupFragment productFamilyDemoFragment = new ConnectingGroupFragment();

        return productFamilyDemoFragment;
    }

    public static ConnectingGroupFragment newInstance(String password) {

        ConnectingGroupFragment productFamilyDemoFragment = new ConnectingGroupFragment();

        return productFamilyDemoFragment;
    }

}
