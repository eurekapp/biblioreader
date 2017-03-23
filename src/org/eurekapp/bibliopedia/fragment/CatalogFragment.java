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
package org.eurekapp.bibliopedia.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;
import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockFragment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import jedi.option.Option;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.eurekapp.bibliopedia.Configuration;
import org.eurekapp.bibliopedia.PlatformUtil;
import org.eurekapp.bibliopedia.activity.FileBrowseActivity;
import org.eurekapp.nucular.atom.AtomConstants;
import org.eurekapp.nucular.atom.Link;
import org.eurekapp.bibliopedia.catalog.Catalog;
import org.eurekapp.bibliopedia.catalog.CatalogListAdapter;
import org.eurekapp.bibliopedia.catalog.CatalogParent;
import org.eurekapp.bibliopedia.catalog.LoadFeedCallback;
import org.eurekapp.bibliopedia.catalog.LoadOPDSTask;
import org.eurekapp.bibliopedia.catalog.LoadThumbnailTask;
import org.eurekapp.bibliopedia.catalog.ParseBinDataTask;
import org.eurekapp.ui.UiUtils;
import org.eurekapp.nucular.atom.Entry;
import org.eurekapp.nucular.atom.Feed;
import org.eurekapp.bibliopedia.R;
import org.eurekapp.ui.DialogFactory;
import org.eurekapp.bibliopedia.scheduling.TaskQueue;
import org.eurekapp.bibliopedia.view.FastBitmapDrawable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import roboguice.inject.InjectView;

import javax.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static jedi.functional.FunctionalPrimitives.isEmpty;
import static jedi.option.Options.option;

public class CatalogFragment extends RoboSherlockFragment implements LoadFeedCallback {
	
	private static final Logger LOG = LoggerFactory
			.getLogger("CatalogFragment");

	@InjectView(R.id.catalogList)
	@Nullable
	private ListView catalogList;

	@Inject
	private Provider<LoadOPDSTask> loadOPDSTaskProvider;

    @Inject
    private Provider<ParseBinDataTask> parseBinDataTaskProvider;

    @Inject
    private Provider<LoadThumbnailTask> loadThumbnailTaskProvider;

    @Inject
    private DialogFactory dialogFactory;

    @Inject
	private CatalogListAdapter adapter;

    @Inject
    private Provider<DisplayMetrics> metricsProvider;

    @Inject
    private TaskQueue taskQueue;

    @Inject
    private Configuration conf;

    private Map<String, Drawable> thumbnailCache = new ConcurrentHashMap<>();

    private MenuItem searchMenuItem;

    private String baseURL;

    private Feed staticFeed;

    private boolean logged;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        DisplayMetrics metrics = metricsProvider.get();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        this.taskQueue.setTaskQueueListener(this::onLoadingDone);
	}



    public void setBaseURL(  String baseURL ) {
        this.baseURL = baseURL;
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_catalog, container, false);
	}

    @Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            login();

        }
		setHasOptionsMenu(true);
		catalogList.setAdapter(adapter);
        adapter.setImageLoader( (baseURL, link) ->
                option(thumbnailCache.get(link.getHref())) );

        catalogList.setOnScrollListener(new LoadingScrollListener());

        catalogList.setOnItemClickListener((list, v, position, a) -> {
            Option<Entry> entry = adapter.getItem(position);
            entry.forEach(e -> onEntryClicked(e, position));
        });

        if ( staticFeed != null ) {
            adapter.setFeed( staticFeed );
        }

	}

    public void onBecameVisible() {
        adapter.getFeed().forEach( f ->
            ((CatalogParent) getActivity() ).onFeedLoaded( f )
        );
    }
	
	private void loadOPDSFeed( Entry entry, String url, boolean asDetailsFeed, boolean asSearchFeed, ResultType resultType ) {

		LoadOPDSTask task = this.loadOPDSTaskProvider.get();
		task.setCallBack(this);



        task.setResultType(resultType);
		task.setAsDetailsFeed(asDetailsFeed);
        task.setAsSearchFeed(asSearchFeed);

        //If we're going to load a completely new feed,
        //cancel all pending downloads.
        if ( resultType == ResultType.REPLACE ) {
            taskQueue.clear();
            taskQueue.executeTask(task, url);
        } else {
            taskQueue.jumpQueueExecuteTask(task, url);
            this.adapter.setLoading(true);
        }

	}

    public void setStaticFeed( Feed feed ) {
        this.staticFeed = feed;
    }

    public void performSearch(String searchTerm) {
    	if (searchTerm != null && searchTerm.length() > 0) {
			String searchString = URLEncoder.encode(searchTerm);
            Option<Feed> feed = adapter.getFeed();

            feed.forEach( f -> f.getSearchLink().forEach( searchLink -> {
                String linkUrl = searchLink.getHref();

                linkUrl = linkUrl.replace( AtomConstants.SEARCH_TERMS,
                        searchString);

                loadURL(null, linkUrl, false, true, ResultType.REPLACE);
            }));
		}
    }

    public boolean supportsSearch() {
        return searchMenuItem.isEnabled();
    }

	public void onSearchRequested() {

        if ( searchMenuItem == null || ! searchMenuItem.isEnabled() ) {
            return;
        }

        if ( searchMenuItem.getActionView() != null ) {
            this.searchMenuItem.expandActionView();
            this.searchMenuItem.getActionView().requestFocus();
        } else {
            dialogFactory.showSearchDialog(R.string.search_books, R.string.enter_query, this::performSearch );
        }
	}

	public void onEntryClicked( Entry entry, int position ) {		
			
		if ( entry.getId() != null && entry.getId().equals(Catalog.CUSTOM_SITES_ID) ) {
            ((CatalogParent) getActivity()).loadCustomSitesFeed();
		} else if ( ! isEmpty( entry.getAlternateLink() ) ) {
			String href = entry.getAlternateLink().unsafeGet().getHref();
			replaceFeed(entry, href, true);
		} else if ( ! isEmpty( entry.getEpubLink() )) {
            loadFakeFeed(entry);
		} else if ( ! isEmpty(entry.getAtomLink()) ) {
			String href = entry.getAtomLink().unsafeGet().getHref();
			replaceFeed( entry, href, false);
		} else if ( ! isEmpty( entry.getWebsiteLink() ) ) {
            String url = entry.getWebsiteLink().unsafeGet().getHref();
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        }
	}

    private void replaceFeed( Entry entry, String href, boolean asDetailsFeed ) {

        String baseURL = entry.getBaseURL();

        if ( baseURL == null ) {
            baseURL = this.baseURL;
        }

        LOG.debug("Loading new Feed with baseURL: " + baseURL);

        ((CatalogParent) getActivity()).loadFeed(entry, href, baseURL, asDetailsFeed);
    }

    private void loadFakeFeed( Entry entry ) {

        Feed fakeFeed = new Feed();
        fakeFeed.addEntry(entry);
        fakeFeed.setTitle(entry.getTitle());
        fakeFeed.setDetailFeed(true);
        fakeFeed.setURL(entry.getBaseURL());

        ((CatalogParent) getActivity()).loadFakeFeed(fakeFeed);
    }

    public void loadURL(Entry entry, String url, boolean asDetailsFeed, boolean asSearchFeed, ResultType resultType) {

        String base = null;

        if ( entry != null  ) {
            base = entry.getBaseURL();
        }

        if ( base == null ) {
            base = this.baseURL;
        }

        LOG.debug("Using baseURL: " + base);

		try {
			String target = url;
			
//			if ( base != null && ! base.equals(Catalog.CUSTOM_SITES_ID)) {
            if ( base != null ) {
				target = new URL(new URL(base), url).toString();
			}

			LOG.info("Loading " + target);

			loadOPDSFeed(entry, target, asDetailsFeed, asSearchFeed, resultType);
		} catch (MalformedURLException u) {
			LOG.error("Malformed URL:", u);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        SherlockFragmentActivity activity = getSherlockActivity();

        if ( activity == null ) {
            return;
        }

		activity.getSupportActionBar().setHomeButtonEnabled(true);
		inflater.inflate(R.menu.catalog_menu, menu);

        this.searchMenuItem = menu.findItem(R.id.search);
        if (searchMenuItem != null) {
            final SearchView searchView = (SearchView) searchMenuItem.getActionView();

            if (searchView != null) {
                searchView.setSubmitButtonEnabled(true);
                searchView.setOnQueryTextListener(UiUtils.onQuery(this::performSearch) );
            } else {
                searchMenuItem.setOnMenuItemClickListener( item -> {
                        dialogFactory.showSearchDialog(R.string.search_books,
                                R.string.enter_query, this::performSearch );
                        return false;
                });
            }
        }
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {

        Option<Feed> feed = adapter.getFeed();

        boolean searchEnabled = !isEmpty( (Option<Link>) feed.flatMap(Feed::getSearchLink) );

		for ( int i=0; i < menu.size(); i++ ) {
			MenuItem item = menu.getItem(i);
			
			boolean enabled;
			
			switch (item.getItemId()) {

			case R.id.search:
				enabled = searchEnabled;
				break;			
			default:
				enabled = true;
			}			
			
			item.setEnabled(enabled);
			item.setVisible(enabled);			
		}

        LOG.debug("Adapter has feed: " + adapter.getFeed());
	}

    @Override
    public void notifyLinkUpdated(Link link, Drawable drawable) {

        if ( drawable != null ) {
            this.thumbnailCache.put(link.getHref(), drawable);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
	public void errorLoadingFeed(String error) {
		if ( isAdded() ) {
            Toast.makeText(getActivity(), getString(R.string.feed_failed) + ": " + error,
				Toast.LENGTH_LONG).show();
        }
	}

    @Override
    public void emptyFeedLoaded(Feed feed) {
        if ( feed.isSearchFeed() ) {
            Toast.makeText(getActivity(), R.string.no_search_results, Toast.LENGTH_LONG ).show();
        } else {
            errorLoadingFeed(getActivity().getString(R.string.empty_opds_feed));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyThumbnails();
    }

    @Override
    public void onLowMemory() {
        destroyThumbnails();
    }

    public void setNewFeed(Feed result, ResultType resultType) {

        if (result != null ) {

            if ( resultType == null || resultType == ResultType.REPLACE ) {
                destroyThumbnails();
                thumbnailCache.clear();
                adapter.setFeed(result);
                if ( isAdded() ) {
                    ((CatalogParent) getActivity() ).onFeedLoaded(result);
                }
            } else {
                this.adapter.setLoading(false);
                adapter.addEntriesFromFeed(result);
            }
        }
    }

    private void destroyThumbnails() {
        for ( Map.Entry<String, Drawable> entry: thumbnailCache.entrySet() ) {
            Drawable value = entry.getValue();

            if ( value instanceof FastBitmapDrawable ) {
                ((FastBitmapDrawable) value).destroy();
            }
        }
    }

    private void queueImageLoading( String baseURL, Link imageLink ) {

        Context context = getActivity();

        if ( context == null ) {
            return;
        }

        //Make sure we only start a single background task for each url
        if ( this.thumbnailCache.containsKey(imageLink.getHref() ) ) {
            return;
        } else {
            this.thumbnailCache.put( imageLink.getHref(), context.getResources().getDrawable(R.drawable.unknown_cover1));
        }

        String href = imageLink.getHref();

        // If the image is contained in the feed, load it
        // directly
        if ( href.startsWith("data:image") ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                ParseBinDataTask binDataTask = this.parseBinDataTaskProvider.get();
                binDataTask.setLoadFeedCallback(this);

                taskQueue.executeTask(binDataTask, imageLink);
            }
        }else {

            LoadThumbnailTask thumbnailTask = this.loadThumbnailTaskProvider.get();

            thumbnailTask.setBaseUrl(baseURL);
            thumbnailTask.setLoadFeedCallback(this);

            taskQueue.executeTask(thumbnailTask, imageLink);
        }
    }

    private void setSupportProgressBarIndeterminateVisibility(boolean enable) {
        SherlockFragmentActivity activity = getSherlockActivity();
        if ( activity != null) {
            LOG.debug("Setting progress bar to " + enable );
            activity.setSupportProgressBarIndeterminateVisibility(enable);
        } else {
            LOG.debug("Got null activity.");
        }
    }

    private void onLoadingDone() {
        LOG.debug("Done loading.");
        setSupportProgressBarIndeterminateVisibility(false);
    }

    @Override
    public void onLoadingStart() {
        LOG.debug("Start loading.");
        setSupportProgressBarIndeterminateVisibility(true);
    }

    private class LoadingScrollListener implements AbsListView.OnScrollListener {

        private static final int LOAD_THRESHOLD = 2;

        private String lastLoadedUrl = "";

        private Handler handler = new Handler();
        private Runnable updater;

        @Override
        public void onScroll(AbsListView view, final int firstVisibleItem, final int visibleItemCount, int totalItemCount) {
            loadThumbnails(firstVisibleItem, visibleItemCount, totalItemCount );
            loadNextFeed(firstVisibleItem, visibleItemCount, totalItemCount );
        }

        private void loadNextFeed( final int firstVisibleItem, final int visibleItemCount, int totalItemCount ) {

            int lastVisibleItem = firstVisibleItem + visibleItemCount;

            if ( totalItemCount - lastVisibleItem  <= LOAD_THRESHOLD && adapter.getCount() > 0) {

                Option<Entry> lastEntry = adapter.getItem( adapter.getCount() -1 );

                lastEntry.flatMap(Entry::getFeed).forEach( feed -> feed.getNextLink().forEach( link -> {
                    if (! link.getHref().equals(lastLoadedUrl)) {
                        Entry nextEntry = new Entry();
                        nextEntry.setFeed(feed);
                        nextEntry.addLink(link);

                        LOG.debug("Starting download for " + link.getHref() + " after scroll");

                        lastLoadedUrl = link.getHref();
                        loadURL(nextEntry, link.getHref(), false, false, ResultType.APPEND);
                    }
                }));
            }
        }

        private void loadThumbnails( final int firstVisibleItem, final int visibleItemCount, int totalItemCount ) {
            if ( updater != null ) {
                handler.removeCallbacks(updater);
            }

            updater = () -> {
                for ( int i=0; i < visibleItemCount; i++ ) {
                    Option<Entry> entry = adapter.getItem( firstVisibleItem + i );

                    entry.forEach( e -> e.getFeed().forEach( feed -> {
                        Catalog.getImageLink( feed, e).forEach( imageLink -> {
                            if ( ! thumbnailCache.containsKey( imageLink.getHref() ) ) {
                                queueImageLoading( e.getBaseURL(), imageLink );
                            }
                        });
                    }));
                }
            };

            long delay;

            if ( firstVisibleItem + visibleItemCount == totalItemCount ) {
                delay = 0; //All items on screen, no wait
            } else {
                delay = 500; //Default delay
            }

            handler.postDelayed( updater, delay );
        }


        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {

        }
    }


    private void login() {


       if (conf.isLogged()) return;

        LayoutInflater inflater = PlatformUtil.getLayoutInflater(getActivity());
        final View layout = inflater.inflate(org.eurekapp.bibliopedia.R.layout.login, null);

        final TextView folder = (TextView) layout.findViewById(org.eurekapp.bibliopedia.R.id.accountmail);
        final Spinner sp = (Spinner) layout.findViewById(R.id.spinner);

        folder.setEnabled(true);


        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Ingresar");
        builder.setMessage("Tiene un correo valido para ingresar al sistema?");



        builder.setView(layout);
        builder.setPositiveButton(android.R.string.yes, (dialog, which) -> {

                    String email = String.valueOf(folder.getText());
                    email = email.trim();
                    Log.d("PIPEEEEEE", email);
                    try {

                        URL url = new URL("http://190.147.155.131:8007/woop3/webresources/entities.user/login/" + Uri.encode(email));
                        Log.d("PIPEEEEEE", url.toString());


                        HttpClient httpclient = new DefaultHttpClient();
                        HttpResponse response = httpclient.execute(new HttpGet(String.valueOf(url)));
                        StatusLine statusLine = response.getStatusLine();

                        if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();

                            response.getEntity().writeTo(out);
                            String responseString = out.toString();
                            Log.d("PIPEE", responseString);

                            out.close();
                            if (responseString.equalsIgnoreCase("true")) {
                                conf.setBaseOPDSFeed("http://190.147.155.131/Bibliopedia/YopalEscolar/catalog.xml");
                                conf.setLogged(true);
                                conf.setEmail(email);
                                Log.d("selecionadoo",sp.getSelectedItem().toString());
                                conf.setInstitution(sp.getSelectedItem().toString());
                                getActivity().setTitle("sp.getSelectedItem().toString()");
                                //ACA CAMBIAMOS CADA UNA DE LAS OPCIONES DE LA INSTITUCION

                            } else {
                                conf.setBaseOPDSFeed("");
                                conf.setLogged(false);
                                AlertDialog.Builder bui = new AlertDialog.Builder(getActivity());
                                bui.setTitle("Incorrecto");
                                bui.setMessage("Lo sentimos, su correo no es válido");
                                bui.show();
                                getActivity().finish();

                            }


                            //..more logic
                        } else {
                            //Closes the connection.
                            try {
                                //conf.setBaseOPDSFeed("");
                                conf.setLogged(false);
                                AlertDialog.Builder bui = new AlertDialog.Builder(getActivity());
                                bui.setTitle("Incorrecto");
                                bui.setMessage("Lo sentimos, su correo no es válido");
                                bui.show();
                                getActivity().finish();
                                if (response.getEntity() != null){
                                response.getEntity().getContent().close();}

                            } catch (IOException e) {
                                Log.d("login", statusLine.getReasonPhrase());
                                e.printStackTrace();
                            }

                        }
                    } catch (IOException e) {
                        Log.d("login", e.toString());
                    }

                    //getLogin();

                    dialog.dismiss();

                }

        );

    builder.setNegativeButton(android.R.string.no, (dialog, which) ->

    {
        dialog.dismiss();
        //android.os.Process.killProcess(android.os.Process.myPid());
        getActivity().finish();

        });

        builder.setCancelable(false);
        builder.show();
    }


}
