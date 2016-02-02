/*
 * Copyright (C) 2012 Alex Kuiper
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
package org.eurekapp.bibliopedia.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.google.inject.Inject;
import com.google.inject.Provider;
import jedi.option.Option;

import org.eurekapp.nucular.atom.AtomConstants;
import org.eurekapp.nucular.atom.Entry;
import org.eurekapp.nucular.atom.Link;
import org.eurekapp.bibliopedia.Configuration;
import org.eurekapp.bibliopedia.CustomOPDSSite;
import org.eurekapp.bibliopedia.catalog.Catalog;
import org.eurekapp.bibliopedia.catalog.CatalogParent;
import org.eurekapp.bibliopedia.catalog.LoadFeedCallback;
import org.eurekapp.bibliopedia.fragment.BookDetailsFragment;
import org.eurekapp.bibliopedia.fragment.CatalogFragment;
import org.eurekapp.nucular.atom.Feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import roboguice.inject.InjectFragment;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static jedi.functional.FunctionalPrimitives.isEmpty;
import static jedi.option.Options.none;
import static jedi.option.Options.option;

public class CatalogActivity extends PageTurnerActivity implements CatalogParent {

    private static final Logger LOG = LoggerFactory
            .getLogger("CatalogActivity");

    @Nullable
    @InjectFragment(org.eurekapp.bibliopedia.R.id.fragment_book_details)
    private BookDetailsFragment detailsFragment;

    @Inject
    private Provider<CatalogFragment> fragmentProvider;

    @Inject
    private FragmentManager fragmentManager;

    @Inject
    private Configuration config;

    private String baseFeedTitle;

    @Override
    protected void onCreatePageTurnerActivity(Bundle savedInstanceState) {
        hideDetailsView();

        //loadCustomSitesFeed();
        loadFeed(null, config.getBaseOPDSFeed(), null, true);
        //loadCustomSitesFeed();
        fragmentManager.addOnBackStackChangedListener( this::onBackStackChanged );
    }

    @Override
    protected int getMainLayoutResource() {
        return org.eurekapp.bibliopedia.R.layout.activity_catalog;
    }

    private void hideDetailsView() {
        if ( detailsFragment != null ) {
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.hide(detailsFragment);
            ft.commitAllowingStateLoss();
        }
    }

    private boolean isTwoPaneView() {
        return  getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && detailsFragment != null;
    }


    @Override
    public void onFeedLoaded(Feed feed) {

        if ( isTwoPaneView() && feed.getSize() == 1
                && feed.getEntries().get(0).getEpubLink() != null ) {
            loadFakeFeed(feed);
        } else {
            hideDetailsView();
        }

        supportInvalidateOptionsMenu();
        getSupportActionBar().setTitle(feed.getTitle());

        LOG.debug("Changed window title to " + feed.getTitle());

        /*
         * Work-around, since the initial fragment isn't put on
         * the back-stack. We do want to restore its title
         * when the stack becomes empty, so we save it here.
         */

        if ( fragmentManager.getBackStackEntryCount() == 0 ) {
            this.baseFeedTitle = feed.getTitle();
        }
    }

    private void onBackStackChanged() {

        LOG.debug( "Backstack change detected." );

        if ( fragmentManager.getBackStackEntryCount() > 0 ) {

            Option<Fragment> fragmentOption = getCurrentVisibleFragment();
            fragmentOption.forEach((fragment) -> {
                if (fragment instanceof CatalogFragment) {
                    LOG.debug("Notifying fragment.");
                    ((CatalogFragment) fragment).onBecameVisible();
                }
            });

        } else if ( baseFeedTitle != null ) {
            supportInvalidateOptionsMenu();
            getSupportActionBar().setTitle( baseFeedTitle);
        }

    }

    @Override
    public void loadFakeFeed(Feed fakeFeed) {

        if ( isTwoPaneView() ) {

            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            ft.show(detailsFragment);
            ft.commit();

            detailsFragment.setNewFeed(fakeFeed, null);
        } else {
            Intent intent = new Intent( this, CatalogBookDetailsActivity.class );
            intent.putExtra("fakeFeed", fakeFeed);

            startActivity(intent);
        }
    }

    @Override
    public void loadCustomSitesFeed() {
        CatalogFragment newCatalogFragment = fragmentProvider.get();

        List<CustomOPDSSite> sites = config.getCustomOPDSSites();

        if ( sites.isEmpty() ) {
            //Toast.makeText(this, R.string.no_custom_sites, Toast.LENGTH_LONG).show();
            //si no tiene sites le agrego el de biblio
            CustomOPDSSite site = new CustomOPDSSite();
            storeBipo();
            return;
        }


        Feed customSites = new Feed();
        customSites.setURL(Catalog.CUSTOM_SITES_ID);
        customSites.setTitle(getString(org.eurekapp.bibliopedia.R.string.custom_site));

//loadFeed(null,"http://190.147.155.131/Bibliopedia/_catalog/allbooks/allbooks_Z_Page_1.xml",null,true);
//        Entry entry = new Entry();
//        entry.setTitle("Zadig, ó El Destino, Historia Oriental");
//        entry.setSummary("");
//
//        Link link = new Link("http://190.147.155.131/Bibliopedia/_catalog/book_1/book_1065.xml", AtomConstants.TYPE_ATOM, AtomConstants.REL_BUY, null);
//        entry.addLink(link);
//        entry.setBaseURL("http://190.147.155.131/Bibliopedia/_catalog/book_1/book_1065.xml");
//        customSites.addEntry(entry);
//
//
//        Entry entry1 = new Entry();
//        entry1.setTitle("Zalacaín el aventurero (historia de las buenas andanzas y fortunas de Martín Zalacaín el aventurero).");
//        entry1.setSummary("");
//
//        Link link1 = new Link("http://190.147.155.131/Bibliopedia/_catalog/book_0/book_493.xml", AtomConstants.TYPE_ATOM, AtomConstants.REL_BUY, null);
//        entry1.addLink(link1);
//        entry1.setBaseURL("http://190.147.155.131/Bibliopedia/_catalog/book_0/book_493.xml");
//        customSites.addEntry(entry1);
//
//
//        Entry entry2 = new Entry();
//        entry2.setTitle("Zaragoza : Episodios Nacionales");
//        entry2.setSummary("");
//
//        Link link2 = new Link("http://190.147.155.131/Bibliopedia/_catalog/book_0/book_637.xml", AtomConstants.TYPE_ATOM, AtomConstants.REL_BUY, null);
//        entry2.addLink(link2);
//        entry2.setBaseURL("http://190.147.155.131/Bibliopedia/_catalog/book_0/book_637.xml");
//        customSites.addEntry(entry2);

        for ( CustomOPDSSite site: sites ) {
            Entry entryx = new Entry();
            entryx.setTitle(site.getName());
            entryx.setSummary(site.getDescription());

            Link linkx = new Link(site.getUrl(), AtomConstants.TYPE_ATOM, AtomConstants.REL_BUY, null);
            entryx.addLink(linkx);
            entryx.setBaseURL(site.getUrl());

            customSites.addEntry(entryx);
        }

        ///METER ALGUNOS LIBROS A MANO
//        String url = "http://190.147.155.131/Bibliopedia/_catalog/allbooks/allbooks_Z_Page_1.xml";
//        String xml = null;
//        try
//        {
//            //default http client
//            HttpClient httpClient = new DefaultHttpClient();
//
//            HttpPost httpPost = new HttpPost(url);
//
//            System.out.println("URL IN PARSER:==="+url+"====");
//
//            HttpResponse httpResponse = httpClient.execute(httpPost);
//

//            HttpEntity httpentity = httpResponse.getEntity();
//
//            xml = EntityUtils.toString(httpentity);   // I have changed it... because  occur while downloading..
//
//            Log.d("response", xml);
//        }
//        catch(UnsupportedEncodingException e)
//        {
//            e.printStackTrace();
//        }
//        catch (ClientProtocolException e)
//        {
//            e.printStackTrace();
//        }
//        catch (IOException e)
//        {
//            e.printStackTrace();
//        }
//
//        System.out.print(xml);


// a mano
//        List<LibraryBook> sites1 = config.getCustomOPDSSites();
//
//        for ( LibraryBook site: sites1 ) {
//            Entry entry = new Entry();
//            entry.setTitle(site.getName());
//            entry.setSummary(site.getDescription());
//
//            Link link = new Link(site.getUrl(), AtomConstants.TYPE_ATOM, AtomConstants.REL_BUY, null);
//            entry.addLink(link);
//            entry.setBaseURL(site.getUrl());
//
//            customSites.addEntry(entry);
//        }

        //CatalogFragment x = (CatalogFragment) getCurrentVisibleFragment();
        //x.loadURL(null, "http://190.147.155.131/Bibliopedia/_catalog/allbooks/allbooks_Z_Page_1.xml", true, false, LoadFeedCallback.ResultType.APPEND);



        customSites.setId(Catalog.CUSTOM_SITES_ID);

        newCatalogFragment.setStaticFeed(customSites);

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

        fragmentTransaction.replace(org.eurekapp.bibliopedia.R.id.fragment_catalog, newCatalogFragment, Catalog.CUSTOM_SITES_ID);
        fragmentTransaction.addToBackStack(Catalog.CUSTOM_SITES_ID);



        fragmentTransaction.commit();
    }



    @Override
    public void loadFeed(Entry entry, String href, String baseURL, boolean asDetailsFeed) {

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

        CatalogFragment newCatalogFragment = fragmentProvider.get();
        newCatalogFragment.setBaseURL(baseURL);

        fragmentTransaction.replace(org.eurekapp.bibliopedia.R.id.fragment_catalog, newCatalogFragment, baseURL);

        if ( ! href.equals( config.getBaseOPDSFeed() ) ) {
            fragmentTransaction.addToBackStack(baseURL);

        }

        fragmentTransaction.commit();

        newCatalogFragment.loadURL(entry, href, asDetailsFeed, false, LoadFeedCallback.ResultType.REPLACE);
    }

    private Option<Fragment> getCurrentVisibleFragment() {

        if ( fragmentManager.getBackStackEntryCount() < 1 ) {
            return none();
        }

        FragmentManager.BackStackEntry entry = fragmentManager.getBackStackEntryAt(
                fragmentManager.getBackStackEntryCount() - 1);


        Option<Fragment> result = option(fragmentManager.findFragmentByTag(entry.getName()));

        if ( isEmpty( result ) ){
            LOG.debug("Could not find fragment with name " + entry.getName());
        }

        return result;
    }


    @Override
    public boolean onSearchRequested() {

        Option<Boolean> result = getCurrentVisibleFragment().map(fragment -> {

            if (fragment instanceof CatalogFragment) {
                CatalogFragment catalogFragment = (CatalogFragment) fragment;

                catalogFragment.onSearchRequested();
                return catalogFragment.supportsSearch();
            }

            return false;
        });

        return result.getOrElse(false);
    }

    @Override
    public void onBackPressed() {
        hideDetailsView();
        super.onBackPressed();
    }
    public void storeBipo( ) {



        final CustomOPDSSite site;
        final CustomOPDSSite site1;
        List<CustomOPDSSite> sites = new ArrayList<CustomOPDSSite>();

        site = new CustomOPDSSite();

        site.setName("Bibliopedia Yopal");
        site.setDescription(" ");
        site.setUrl("http://190.147.155.131:80/Bibliopedia/_catalog/index.xml");
        sites.add(site);

        final CustomOPDSSite site2 ;
        site2 = new CustomOPDSSite();
        site2.setName("Bibliopedia Drama");
        site2.setDescription(" ");
        site2.setUrl("http://190.147.155.131:80/Bibliopedia/_catalog2/index.xml");
        sites.add(site2);

        final CustomOPDSSite site3 ;
        site3 = new CustomOPDSSite();
        site3.setName("Bibliopedia Ficcion");
        site3.setDescription(" ");
        site3.setUrl("http://190.147.155.131:80/Bibliopedia/_catalog3/index.xml");
        sites.add(site3);



        site1 = new CustomOPDSSite();
        site1.setName("Immersive English Yopal");
        site1.setDescription(" ");
        site1.setUrl("http://190.147.155.131:80/Bibliopedia/_catalog1/index.xml");
        sites.add(site1);
        //site.setUserName("biblio");
        //site.setPassword("biblio");

        //	adapter.add(site);




        config.storeCustomOPDSSites(sites);
        //adapter.notifyDataSetChanged();
    }



}
