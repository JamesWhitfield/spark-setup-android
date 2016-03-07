package io.particle.devicesetup.exampleapp;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;

import com.squareup.phrase.Phrase;

import java.util.Arrays;

import io.particle.android.sdk.accountsetup.LoginActivity;
import io.particle.android.sdk.cloud.ParticleCloud;
import io.particle.android.sdk.cloud.ParticleCloudException;
import io.particle.android.sdk.cloud.Responses;
import io.particle.android.sdk.devicesetup.ui.DeviceSetupState;
import io.particle.android.sdk.devicesetup.ui.DiscoverDeviceActivity;
import io.particle.android.sdk.devicesetup.ui.GetReadyActivity;
import io.particle.android.sdk.devicesetup.ui.PermissionsFragment;
import io.particle.android.sdk.utils.Async;
import io.particle.android.sdk.utils.SoftAPConfigRemover;
import io.particle.android.sdk.utils.ui.Ui;
import io.particle.android.sdk.utils.ui.WebViewActivity;

import static io.particle.android.sdk.utils.Py.truthy;

/**
 * Created by jwhit on 07/03/2016.
 */
public class GetGroupReadyActivity extends GetReadyActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_group_ready);

        sparkCloud = ParticleCloud.get(this);
        softAPConfigRemover = new SoftAPConfigRemover(this);
        softAPConfigRemover.removeAllSoftApConfigs();
        softAPConfigRemover.reenableWifiNetworks();

        PermissionsFragment.ensureAttached(this);

        Ui.findView(this, io.particle.android.sdk.devicesetup.R.id.action_im_ready).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onReadyButtonClicked();
                    }
                }
        );
        Ui.setTextFromHtml(this, io.particle.android.sdk.devicesetup.R.id.action_troubleshooting, io.particle.android.sdk.devicesetup.R.string.troubleshooting)
                .setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Uri uri = Uri.parse(v.getContext().getString(io.particle.android.sdk.devicesetup.R.string.troubleshooting_uri));
                        startActivity(WebViewActivity.buildIntent(v.getContext(), uri));
                    }
                });

        Ui.setText(this, R.id.get_ready_group_text,
                Phrase.from(this, R.string.get_ready_group_text)
                        .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                        .put("indicator_light_setup_color_name", getString(io.particle.android.sdk.devicesetup.R.string.listen_mode_led_color_name))
                        .put("setup_button_identifier", getString(io.particle.android.sdk.devicesetup.R.string.mode_button_name))
                        .format());

        Ui.setText(this, R.id.get_ready_group_text_title,
                Phrase.from(this, R.string.get_ready_group_title_text)
                        .put("device_name", getString(io.particle.android.sdk.devicesetup.R.string.device_name))
                        .format());
    }

    protected void onReadyButtonClicked() {
        // FIXME: check here that another of these tasks isn't already running
        DeviceSetupState.reset();
        showProgress(true);
        final Context ctx = this;
        claimCodeWorker = Async.executeAsync(sparkCloud, new Async.ApiWork<ParticleCloud, Responses.ClaimCodeResponse>() {
            @Override
            public Responses.ClaimCodeResponse callApi(ParticleCloud sparkCloud) throws ParticleCloudException {
                Resources res = ctx.getResources();
                if (res.getBoolean(io.particle.android.sdk.devicesetup.R.bool.organization)) {
                    return sparkCloud.generateClaimCodeForOrg(
                            res.getString(io.particle.android.sdk.devicesetup.R.string.organization_slug),
                            res.getString(io.particle.android.sdk.devicesetup.R.string.product_slug));
                } else {
                    return sparkCloud.generateClaimCode();
                }
            }

            @Override
            public void onTaskFinished() {
                claimCodeWorker = null;
                showProgress(false);
            }

            @Override
            public void onSuccess(Responses.ClaimCodeResponse result) {
                log.d("Claim code generated: " + result.claimCode);

                DeviceSetupState.claimCode = result.claimCode;
                if (truthy(result.deviceIds)) {
                    DeviceSetupState.claimedDeviceIds.addAll(Arrays.asList(result.deviceIds));
                }

                if (isFinishing()) {
                    return;
                }

                moveToDeviceDiscovery();
            }

            @Override
            public void onFailure(ParticleCloudException error) {
                log.d("Generating claim code failed");
                ParticleCloudException.ResponseErrorData errorData = error.getResponseData();
                if (errorData != null && errorData.getHttpStatusCode() == 401) {

                    if (isFinishing()) {
                        sparkCloud.logOut();
                        startLoginActivity();
                        return;
                    }

                    String errorMsg = String.format("Sorry, you must be logged in as a %s customer.",
                            getString(io.particle.android.sdk.devicesetup.R.string.brand_name));
                    new AlertDialog.Builder(GetGroupReadyActivity.this)
                            .setTitle(io.particle.android.sdk.devicesetup.R.string.access_denied)
                            .setMessage(errorMsg)
                            .setPositiveButton(io.particle.android.sdk.devicesetup.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    log.i("Logging out user");
                                    sparkCloud.logOut();
                                    startLoginActivity();
                                    finish();
                                }
                            })
                            .show();

                } else {
                    if (isFinishing()) {
                        return;
                    }

                    // FIXME: we could just check the internet connection here ourselves...
                    String errorMsg = getString(io.particle.android.sdk.devicesetup.R.string.get_ready_could_not_connect_to_cloud);
                    if (error.getMessage() != null) {
                        errorMsg = errorMsg + "\n\n" + error.getMessage();
                    }
                    new AlertDialog.Builder(GetGroupReadyActivity.this)
                            .setTitle(io.particle.android.sdk.devicesetup.R.string.error)
                            .setMessage(errorMsg)
                            .setPositiveButton(io.particle.android.sdk.devicesetup.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .show();
                }
            }
        });
    }

    private void moveToDeviceDiscovery() {
        if (PermissionsFragment.hasPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            startActivity(new Intent(GetGroupReadyActivity.this, DiscoverGroupDeviceActivity.class));
        } else {
            PermissionsFragment.get(this).ensurePermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
    }

    private void startLoginActivity() {
        startActivity(new Intent(this, LoginActivity.class));
    }
}
