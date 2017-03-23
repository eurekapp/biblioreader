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
package org.eurekapp.bibliopedia.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.eurekapp.nucular.atom.Entry;
import org.eurekapp.nucular.atom.Feed;
import org.eurekapp.bibliopedia.catalog.CatalogParent;
import org.eurekapp.bibliopedia.fragment.BookDetailsFragment;

import java.io.Serializable;

public class CatalogBookDetailsActivity extends BibliopediaActivity implements CatalogParent {

    private BookDetailsFragment detailsFragment;

    @Override
    protected void onCreateBibliopediaActivity(Bundle savedInstanceState) {
        if (getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            // If the screen is now in landscape mode, we can show the
            // dialog in-line with the list so we don't need this activity.
            finish();
            return;
        }

        detailsFragment = (BookDetailsFragment) getSupportFragmentManager().findFragmentById(org.eurekapp.bibliopedia.R.id.fragment_bookdetails);

        Intent intent = getIntent();
        Uri uri = intent.getData();

        if (uri != null && uri.toString().startsWith("epub://")) {
            String downloadUrl = uri.toString().replace("epub://", "http://");
            detailsFragment.startDownload(false, downloadUrl);
        } else {
            Serializable ser = intent.getSerializableExtra("fakeFeed");
            Feed fakeFeed = (Feed) ser;

            if ( fakeFeed != null ) {
                detailsFragment.setNewFeed(fakeFeed, null);
            }
        }
    }

    @Override
    protected int getMainLayoutResource() {
        return org.eurekapp.bibliopedia.R.layout.activity_catalog_details;
    }

    @Override
    public void loadFakeFeed(Feed fakeFeed) {

    }

    @Override
    public void onFeedLoaded(Feed feed) {

    }

    @Override
    public void loadFeed(Entry entry, String href, String baseURL, boolean asDetailsFeed) {

    }

    @Override
    public void loadCustomSitesFeed() {

    }
}
