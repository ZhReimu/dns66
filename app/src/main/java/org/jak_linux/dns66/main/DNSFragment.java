/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

public class DNSFragment extends Fragment implements FloatingActionButtonFragment {

    private ItemRecyclerViewAdapter mAdapter;

    public DNSFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_dns, container, false);

        RecyclerView mRecyclerView = (RecyclerView) rootView.findViewById(R.id.dns_entries);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new ItemRecyclerViewAdapter(MainActivity.config.dnsServers.items, 2);
        mRecyclerView.setAdapter(mAdapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelperCallback(mAdapter));
        itemTouchHelper.attachToRecyclerView(mRecyclerView);

        Switch dnsEnabled = (Switch) rootView.findViewById(R.id.dns_enabled);
        dnsEnabled.setChecked(MainActivity.config.dnsServers.enabled);
        dnsEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MainActivity.config.dnsServers.enabled = isChecked;
            FileHelper.writeSettings(getContext(), MainActivity.config);
        });
        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "dns");
        return rootView;
    }

    @Override
    public void setupFloatingActionButton(FloatingActionButton fab) {
        fab.setOnClickListener(view -> {
            MainActivity main = (MainActivity) getActivity();
            main.editItem(2, null, item -> {
                MainActivity.config.dnsServers.items.add(item);
                mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
                FileHelper.writeSettings(getContext(), MainActivity.config);
            });
        });
    }
}
