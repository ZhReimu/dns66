/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jak_linux.dns66.BuildConfig;
import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity showing a list of apps that are allowlisted by the VPN.
 *
 * @author Braden Farmer
 */
public class AllowlistFragment extends Fragment {

    private static final String TAG = "Allowlist";
    private AppListGenerator appListGenerator;
    private RecyclerView appList;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View rootView = inflater.inflate(R.layout.activity_allowlist, container, false);

        appList = (RecyclerView) rootView.findViewById(R.id.list);
        appList.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        appList.setLayoutManager(layoutManager);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(appList.getContext(),
                DividerItemDecoration.VERTICAL);
        appList.addItemDecoration(dividerItemDecoration);


        swipeRefresh = (SwipeRefreshLayout) rootView.findViewById(R.id.swiperefresh);
        swipeRefresh.setOnRefreshListener(
                () -> {
                    appListGenerator = new AppListGenerator(getContext());
                    appListGenerator.execute();
                }
        );
        swipeRefresh.setRefreshing(true);

        Switch switchShowSystemApps = (Switch) rootView.findViewById(R.id.switch_show_system_apps);
        switchShowSystemApps.setChecked(MainActivity.config.allowlist.showSystemApps);
        switchShowSystemApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MainActivity.config.allowlist.showSystemApps = isChecked;
            FileHelper.writeSettings(getContext(), MainActivity.config);
            appListGenerator = new AppListGenerator(getContext());
            appListGenerator.execute();
        });

        final TextView allowlistDefaultText = (TextView) rootView.findViewById(R.id.allowlist_default_text);
        allowlistDefaultText.setText(getResources().getStringArray(R.array.allowlist_defaults)[MainActivity.config.allowlist.defaultMode]);
        View.OnClickListener onDefaultChangeClicked = v -> {
            PopupMenu menu = new PopupMenu(getContext(), rootView.findViewById(R.id.change_default));
            menu.inflate(R.menu.allowlist_popup);
            menu.setOnMenuItemClickListener(item -> {
                Log.d(TAG, "onMenuItemClick: Setting" + item);
                switch (item.getItemId()) {
                    case R.id.allowlist_default_on_vpn:
                        Log.d(TAG, "onMenuItemClick: OnVpn");
                        MainActivity.config.allowlist.defaultMode = Configuration.Allowlist.DEFAULT_MODE_ON_VPN;
                        break;
                    case R.id.allowlist_default_not_on_vpn:
                        Log.d(TAG, "onMenuItemClick: NotOnVpn");
                        MainActivity.config.allowlist.defaultMode = Configuration.Allowlist.DEFAULT_MODE_NOT_ON_VPN;
                        break;
                    case R.id.allowlist_default_intelligent:
                        Log.d(TAG, "onMenuItemClick: Intelligent");
                        MainActivity.config.allowlist.defaultMode = Configuration.Allowlist.DEFAULT_MODE_INTELLIGENT;
                        break;
                }

                allowlistDefaultText.setText(getResources().getStringArray(R.array.allowlist_defaults)[MainActivity.config.allowlist.defaultMode]);
                appListGenerator = new AppListGenerator(getContext());
                appListGenerator.execute();
                FileHelper.writeSettings(getContext(), MainActivity.config);
                return true;
            });

            menu.show();
        };

        rootView.findViewById(R.id.change_default).setOnClickListener(onDefaultChangeClicked);
        allowlistDefaultText.setOnClickListener(onDefaultChangeClicked);

        appListGenerator = new AppListGenerator(getContext());
        appListGenerator.execute();

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "allowlist");


        return rootView;
    }

    private class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

        public final ArrayList<ListEntry> list;
        public final PackageManager pm;

        final Set<String> onVpn = new HashSet<>();
        final Set<String> notOnVpn = new HashSet<>();

        public AppListAdapter(PackageManager pm, ArrayList<ListEntry> list) {
            this.list = list;
            this.pm = pm;
            MainActivity.config.allowlist.resolve(pm, onVpn, notOnVpn);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.allowlist_row, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final ListEntry entry = list.get(position);

            if (holder.task != null)
                holder.task.cancel(true);

            holder.task = null;
            final Drawable icon = entry.getIcon();
            if (icon != null) {
                holder.icon.setImageDrawable(icon);
                holder.icon.setVisibility(View.VISIBLE);
            } else {
                holder.icon.setVisibility(View.INVISIBLE);

                holder.task = new AsyncTask<ListEntry, Void, Drawable>() {
                    @Override
                    protected Drawable doInBackground(ListEntry... entries) {
                        return entries[0].loadIcon(pm);
                    }

                    @Override
                    protected void onPostExecute(Drawable drawable) {
                        if (!isCancelled()) {
                            holder.icon.setImageDrawable(drawable);
                            holder.icon.setVisibility(View.VISIBLE);
                        }
                        super.onPostExecute(drawable);
                    }
                };

                holder.task.execute(entry);
            }

            holder.name.setText(entry.getLabel());
            holder.details.setText(entry.getPackageName());
            holder.allowlistSwitch.setOnCheckedChangeListener(null);
            holder.allowlistSwitch.setChecked(notOnVpn.contains(entry.getPackageName()));
            holder.allowlistSwitch.setOnCheckedChangeListener((compoundButton, checked) -> {
                /* No change, do nothing */
                if (checked && MainActivity.config.allowlist.items.contains(entry.getPackageName()))
                    return;
                if (!checked && MainActivity.config.allowlist.itemsOnVpn.contains(entry.getPackageName()))
                    return;
                if (checked) {
                    MainActivity.config.allowlist.items.add(entry.getPackageName());
                    MainActivity.config.allowlist.itemsOnVpn.remove(entry.getPackageName());
                    notOnVpn.add(entry.getPackageName());
                } else {
                    MainActivity.config.allowlist.items.remove(entry.getPackageName());
                    MainActivity.config.allowlist.itemsOnVpn.add(entry.getPackageName());
                    notOnVpn.remove(entry.getPackageName());
                }
                FileHelper.writeSettings(getActivity(), MainActivity.config);
            });


            holder.itemView.setOnClickListener(view -> holder.allowlistSwitch.setChecked(!holder.allowlistSwitch.isChecked()));

        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView details;
            final Switch allowlistSwitch;
            AsyncTask<ListEntry, Void, Drawable> task;

            public ViewHolder(View itemView) {
                super(itemView);
                icon = (ImageView) itemView.findViewById(R.id.app_icon);
                name = (TextView) itemView.findViewById(R.id.name);
                details = (TextView) itemView.findViewById(R.id.details);
                allowlistSwitch = (Switch) itemView.findViewById(R.id.checkbox);
            }
        }
    }

    private final class AppListGenerator extends AsyncTask<Void, Void, AppListAdapter> {
        private final PackageManager pm;

        private AppListGenerator(Context context) {
            pm = context.getPackageManager();
        }

        @Override
        protected AppListAdapter doInBackground(Void... params) {
            List<ApplicationInfo> info = pm.getInstalledApplications(0);

            info.sort(new ApplicationInfo.DisplayNameComparator(pm));

            final ArrayList<ListEntry> entries = new ArrayList<>();
            for (ApplicationInfo appInfo : info) {
                if (!appInfo.packageName.equals(BuildConfig.APPLICATION_ID) && (MainActivity.config.allowlist.showSystemApps || (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0))
                    entries.add(new ListEntry(
                            appInfo,
                            appInfo.packageName,
                            appInfo.loadLabel(pm).toString()));
            }

            return new AppListAdapter(pm, entries);
        }

        @Override
        protected void onPostExecute(AppListAdapter adapter) {
            appList.setAdapter(adapter);
            swipeRefresh.setRefreshing(false);
        }
    }

    private static class ListEntry {
        private final ApplicationInfo appInfo;
        private final String packageName;
        private final String label;
        private WeakReference<Drawable> weakIcon;

        private ListEntry(ApplicationInfo appInfo, String packageName, String label) {
            this.appInfo = appInfo;
            this.packageName = packageName;
            this.label = label;
        }

        private String getPackageName() {
            return packageName;
        }

        private String getLabel() {
            return label;
        }

        private ApplicationInfo getAppInfo() {
            return appInfo;
        }

        private Drawable getIcon() {
            return weakIcon != null ? weakIcon.get() : null;
        }

        private Drawable loadIcon(PackageManager pm) {
            Drawable icon = weakIcon != null ? weakIcon.get() : null;
            if (icon == null) {
                icon = appInfo.loadIcon(pm);
                weakIcon = new WeakReference<>(icon);
            }
            return icon;
        }
    }
}