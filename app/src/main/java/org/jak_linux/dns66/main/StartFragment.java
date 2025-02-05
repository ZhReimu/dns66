/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;
import org.jak_linux.dns66.vpn.AdVpnService;
import org.jak_linux.dns66.vpn.Command;

import java.io.IOException;
import java.io.InputStreamReader;

public class StartFragment extends Fragment {
    public static final int REQUEST_START_VPN = 1;
    private static final String TAG = "StartFragment";

    public StartFragment() {
    }

    private static Drawable getDrawable(Context context, int res) {
        return AppCompatResources.getDrawable(context, res);
    }

    public static void updateStatus(View rootView, int status) {
        Context context = rootView.getContext();
        TextView stateText = (TextView) rootView.findViewById(R.id.state_textview);
        ImageView stateImage = (ImageView) rootView.findViewById(R.id.state_image);
        Button startButton = (Button) rootView.findViewById(R.id.start_button);

        if (stateImage == null || stateText == null)
            return;

        stateText.setText(rootView.getContext().getString(AdVpnService.vpnStatusToTextId(status)));
        stateImage.setContentDescription(rootView.getContext().getString(AdVpnService.vpnStatusToTextId(status)));
        stateImage.setImageAlpha(255);
        stateImage.setImageTintList(ContextCompat.getColorStateList(context, R.color.colorStateImage));
        switch (status) {
            case AdVpnService.VPN_STATUS_RECONNECTING:
            case AdVpnService.VPN_STATUS_STARTING:
            case AdVpnService.VPN_STATUS_STOPPING:
                stateImage.setImageDrawable(getDrawable(context, R.drawable.ic_settings_black_24dp));
                startButton.setText(R.string.action_stop);
                break;
            case AdVpnService.VPN_STATUS_STOPPED:
                stateImage.setImageAlpha(32);
                stateImage.setImageTintList(null);
                stateImage.setImageDrawable(getDrawable(context, R.mipmap.app_icon_large));
                startButton.setText(R.string.action_start);
                break;
            case AdVpnService.VPN_STATUS_RUNNING:
                stateImage.setImageDrawable(getDrawable(context, R.drawable.ic_verified_user_black_24dp));
                startButton.setText(R.string.action_stop);
                break;
            case AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR:
                stateImage.setImageDrawable(getDrawable(context, R.drawable.ic_error_black_24dp));
                startButton.setText(R.string.action_stop);
                break;
        }
    }

    private boolean startStopService() {
        if (AdVpnService.vpnStatus != AdVpnService.VPN_STATUS_STOPPED) {
            Log.i(TAG, "Attempting to disconnect");

            Intent intent = new Intent(getActivity(), AdVpnService.class);
            intent.putExtra("COMMAND", Command.STOP.ordinal());
            requireActivity().startService(intent);
        } else {
            checkHostsFilesAndStartService();
        }
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_start, container, false);
        Switch switchOnBoot = (Switch) rootView.findViewById(R.id.switch_onboot);

        ImageView view = (ImageView) rootView.findViewById(R.id.state_image);

        view.setOnLongClickListener(v -> startStopService());

        Button startButton = (Button) rootView.findViewById(R.id.start_button);
        startButton.setOnClickListener(v -> startStopService());

        updateStatus(rootView, AdVpnService.vpnStatus);

        switchOnBoot.setChecked(MainActivity.config.autoStart);
        switchOnBoot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MainActivity.config.autoStart = isChecked;
            FileHelper.writeSettings(getContext(), MainActivity.config);
        });

        Switch watchDog = (Switch) rootView.findViewById(R.id.watchdog);
        watchDog.setChecked(MainActivity.config.watchDog);
        watchDog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MainActivity.config.watchDog = isChecked;
            FileHelper.writeSettings(getContext(), MainActivity.config);

            if (isChecked) {
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.unstable_feature)
                        .setMessage(R.string.unstable_watchdog_message)
                        .setNegativeButton(R.string.button_cancel, (dialog, which) -> {
                            watchDog.setChecked(false);
                            MainActivity.config.watchDog = false;
                            FileHelper.writeSettings(getContext(), MainActivity.config);
                        })
                        .setPositiveButton(R.string.button_continue, (dialog, which) -> {
                            /* Do nothing */
                        })
                        .show();
            }
        });

        Switch ipV6Support = (Switch) rootView.findViewById(R.id.ipv6_support);
        ipV6Support.setChecked(MainActivity.config.ipV6Support);
        ipV6Support.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MainActivity.config.ipV6Support = isChecked;
            FileHelper.writeSettings(getContext(), MainActivity.config);
        });

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "start");

        return rootView;
    }

    private void checkHostsFilesAndStartService() {
        if (!areHostsFilesExistant()) {
            new AlertDialog.Builder(getActivity())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.missing_hosts_files_title)
                    .setMessage(R.string.missing_hosts_files_message)
                    .setNegativeButton(R.string.button_no, (dialog, which) -> {
                        /* Do nothing */
                    })
                    .setPositiveButton(R.string.button_yes, (dialog, which) -> startService())
                    .show();
            return;
        }
        startService();
    }

    private void startService() {
        Log.i(TAG, "Attempting to connect");
        Intent intent = VpnService.prepare(getContext());
        if (intent != null) {
            startActivityForResult(intent, REQUEST_START_VPN);
        } else {
            onActivityResult(REQUEST_START_VPN, RESULT_OK, null);
        }
    }

    /**
     * Check if all configured hosts files exist.
     *
     * @return true if all host files exist or no host files were configured.
     */
    private boolean areHostsFilesExistant() {
        if (!MainActivity.config.hosts.enabled)
            return true;

        for (Configuration.Item item : MainActivity.config.hosts.items) {
            if (item.state != Configuration.Item.STATE_IGNORE) {
                try {
                    InputStreamReader reader = FileHelper.openItemFile(getContext(), item);
                    if (reader == null)
                        continue;

                    reader.close();
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: Received result=" + resultCode + " for request=" + requestCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_CANCELED) {
            Toast.makeText(getContext(), R.string.could_not_configure_vpn_service, Toast.LENGTH_LONG).show();
        }
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_OK) {
            Log.d("MainActivity", "onActivityResult: Starting service");
            Intent intent = new Intent(getContext(), AdVpnService.class);
            intent.putExtra("COMMAND", Command.START.ordinal());
            intent.putExtra("NOTIFICATION_INTENT",
                    PendingIntent.getActivity(getContext(), 0,
                            new Intent(getContext(), MainActivity.class), 0));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent);
            } else {
                requireContext().startService(intent);
            }

        }
    }
}
