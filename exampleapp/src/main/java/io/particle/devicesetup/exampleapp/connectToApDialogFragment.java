package io.particle.devicesetup.exampleapp;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.squareup.phrase.Phrase;

/**
 * Created by jwhit on 09/03/2016.
 */
public class connectToApDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        String msg = Phrase.from(getContext(), io.particle.android.sdk.devicesetup.R.string.connecting_to_soft_ap)
                .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                .format().toString();

        ProgressDialog connectToApSpinnerDialog;

        connectToApSpinnerDialog = new ProgressDialog(getContext());
        connectToApSpinnerDialog.setMessage(msg);
        connectToApSpinnerDialog.setCancelable(false);
        connectToApSpinnerDialog.setIndeterminate(true);
        return connectToApSpinnerDialog;
    }
}
