/*
 * Copyright (C) 2013 Alex Kuiper
 *
 * This file is part of PageTurner
 *
 * PageTurner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PageTurner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PageTurner.  If not, see <http://www.gnu.org/licenses/>.*
 */
package net.eurekapp.pageturner.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.github.rtyley.android.sherlock.roboguice.activity.RoboSherlockListActivity;
import com.google.inject.Inject;

import net.eurekapp.pageturner.Configuration;
import net.eurekapp.pageturner.CustomOPDSSite;
import net.eurekapp.pageturner.PageTurner;
import net.eurekapp.pageturner.PlatformUtil;

import roboguice.RoboGuice;

import java.util.ArrayList;
import java.util.List;

public class ManageSitesActivity extends RoboSherlockListActivity {

	@Inject
	Configuration config;
	
	private CustomOPDSSiteAdapter adapter;
	
	private static enum ContextAction { EDIT, DELETE };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Configuration config = RoboGuice.getInjector(this).getInstance(Configuration.class); 
		PageTurner.changeLanguageSetting(this, config);
		setTheme( config.getTheme() );
		
		super.onCreate(savedInstanceState);		
	
		List<CustomOPDSSite> sites = config.getCustomOPDSSites();
			
		this.adapter = new CustomOPDSSiteAdapter(sites);
		setListAdapter(this.adapter);
		registerForContextMenu(getListView());
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(net.eurekapp.pageturner.R.menu.edit_sites_menu, menu);

		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(
			com.actionbarsherlock.view.MenuItem item) {
		showAddSiteDialog();
		return true;
	}
		
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		
		menu.add(Menu.NONE, ContextAction.EDIT.ordinal(), Menu.NONE, net.eurekapp.pageturner.R.string.edit );
		menu.add(Menu.NONE, ContextAction.DELETE.ordinal(), Menu.NONE, net.eurekapp.pageturner.R.string.delete);
		
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		showEditDialog(adapter.getItem(position));
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		
		ContextAction action = ContextAction.values()[item.getItemId()];
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		CustomOPDSSite site = adapter.getItem(info.position);
		
		switch (action) {
		
		case EDIT:
			showEditDialog(site);
			break;
		case DELETE:
			 adapter.remove(adapter.getItem(info.position));
			 storeSites();	
		}		
	   
	    return true;
	}
	
	private void storeSites() {
		List<CustomOPDSSite> sites = new ArrayList<CustomOPDSSite>();
		for ( int i=0; i < adapter.getCount(); i++ ) {
			sites.add( adapter.getItem(i));
		}
		
		config.storeCustomOPDSSites(sites);
	}
	
	private void showEditDialog(final CustomOPDSSite site) {
		showSiteDialog(net.eurekapp.pageturner.R.string.edit_site, site);
	}
	
	private void showAddSiteDialog() {		
		showSiteDialog(net.eurekapp.pageturner.R.string.add_site, null);
	}
	
	private void showSiteDialog(int titleResource, final CustomOPDSSite siteParam ) {
		
		final CustomOPDSSite site;
		
		if ( siteParam == null ) {
			site = new CustomOPDSSite();
		} else {
			site = siteParam;
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(titleResource);
		LayoutInflater inflater = PlatformUtil.getLayoutInflater(this);
		
		View layout = inflater.inflate(net.eurekapp.pageturner.R.layout.edit_site, null);
		builder.setView(layout);
		
		final TextView siteName = (TextView) layout.findViewById(net.eurekapp.pageturner.R.id.siteName);
		final TextView siteURL = (TextView) layout.findViewById(net.eurekapp.pageturner.R.id.siteUrl);
		final TextView siteDesc = (TextView) layout.findViewById(net.eurekapp.pageturner.R.id.siteDescription);
		final TextView userName = (TextView) layout.findViewById(net.eurekapp.pageturner.R.id.username);
		final TextView password = (TextView) layout.findViewById(net.eurekapp.pageturner.R.id.password);
		
		siteName.setText(site.getName());
		siteURL.setText(site.getUrl());
		siteDesc.setText(site.getDescription());
		userName.setText(site.getUserName());
		password.setText(site.getPassword());

        builder.setPositiveButton(net.eurekapp.pageturner.R.string.save, (dialog, which) -> {
            if ( siteName.getText().toString().trim().length() == 0 ) {
                Toast.makeText(ManageSitesActivity.this, net.eurekapp.pageturner.R.string.msg_name_blank, Toast.LENGTH_SHORT).show();
                return;
            }

            if ( siteURL.getText().toString().trim().length() == 0 ) {
                Toast.makeText(ManageSitesActivity.this, net.eurekapp.pageturner.R.string.msg_url_blank, Toast.LENGTH_SHORT).show();
                return;
            }

            site.setName(siteName.getText().toString());
            site.setDescription(siteDesc.getText().toString());
            site.setUrl(siteURL.getText().toString());
            site.setUserName(userName.getText().toString());
            site.setPassword(password.getText().toString());

            if ( siteParam == null ) {
                adapter.add(site);
            }

            storeSites();
            adapter.notifyDataSetChanged();
            dialog.dismiss();
        });
		
		builder.setNegativeButton(android.R.string.cancel, null );		
	
		builder.show();	
	}

//	public void storeBipo( ) {
//
//		final CustomOPDSSite site;
//		final CustomOPDSSite site1;
//		List<CustomOPDSSite> sites = new ArrayList<CustomOPDSSite>();
//
//			site = new CustomOPDSSite();
//			site.setName("Bibliopedia Yopal");
//			site.setDescription(" ");
//			site.setUrl("http://190.147.155.131:80/Bibliopedia/_catalog/index.xml");
//			sites.add(site);
//
//			final CustomOPDSSite site2 ;
//			site2 = new CustomOPDSSite();
//			site2.setName("Bibliopedia Drama");
//			site2.setDescription(" ");
//			site2.setUrl("http://190.147.155.131:80/Bibliopedia/_catalog2/index.xml");
//			sites.add(site2);
//
//			final CustomOPDSSite site3 ;
//			site3 = new CustomOPDSSite();
//			site3.setName("Bibliopedia Ficcion");
//			site3.setDescription(" ");
//			site3.setUrl("http://190.147.155.131:80/Bibliopedia/_catalog3/index.xml");
//			sites.add(site3);
//
//
//			//site.setUserName("biblio");
//			//site.setPassword("biblio");
//
//			//	adapter.add(site);
//
//
//		site1 = new CustomOPDSSite();
//		site1.setName("Immersive English Yopal");
//		site1.setDescription(" ");
//		site1.setUrl("http://190.147.155.131:80/Bibliopedia/_catalog1/index.xml");
//		sites.add(site1);
//		for ( int i=0; i < adapter.getCount(); i++ ) {
//			sites.add( adapter.getItem(i));
//		}
//
//		config.storeCustomOPDSSites(sites);
//			//adapter.notifyDataSetChanged();
//
//	}

	private class CustomOPDSSiteAdapter extends ArrayAdapter<CustomOPDSSite> {
		public CustomOPDSSiteAdapter(List<CustomOPDSSite> sites) {
			super(ManageSitesActivity.this, 0, sites);
		}		
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			View view;
			
			if ( convertView != null ) {
				view = convertView;
			} else {
				view = ( (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE) ).inflate(net.eurekapp.pageturner.R.layout.manage_sites, null);
			}
			
			TextView siteName = (TextView) view.findViewById( net.eurekapp.pageturner.R.id.siteName );
			TextView description = (TextView) view.findViewById( net.eurekapp.pageturner.R.id.siteDescription );
			
			CustomOPDSSite site = this.getItem(position);
			siteName.setText( site.getName() );
			description.setText( site.getDescription() );
			
			return view;
		}
		
	}

}
