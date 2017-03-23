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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockFragment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import jedi.option.Option;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.eurekapp.nucular.atom.Entry;
import org.eurekapp.nucular.atom.Link;
import org.eurekapp.bibliopedia.Configuration;
import org.eurekapp.bibliopedia.catalog.Catalog;
import org.eurekapp.bibliopedia.catalog.DownloadFileTask;
import org.eurekapp.nucular.atom.Feed;
import org.eurekapp.bibliopedia.R;
import org.eurekapp.bibliopedia.activity.ReadingActivity;
import org.eurekapp.bibliopedia.catalog.LoadFeedCallback;
import org.eurekapp.bibliopedia.catalog.LoadThumbnailTask;

import roboguice.inject.InjectView;

import javax.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Random;

import static jedi.functional.FunctionalPrimitives.isEmpty;

/**
 * Fragment which shows the details of the book to be downloaded.
 */
public class BookDetailsFragment extends RoboSherlockFragment implements LoadFeedCallback {


    @Inject
    private Provider<LoadThumbnailTask> loadThumbnailTaskProvider;

    @Inject
    private Provider<DownloadFileTask> downloadFileTaskProvider;

    @Inject
    private Provider<DisplayMetrics> metricsProvider;

    @InjectView(org.eurekapp.bibliopedia.R.id.mainLayout)
    private View mainLayout;

    @InjectView(R.id.itemAuthor)
    private TextView authorTextView;

    @InjectView(org.eurekapp.bibliopedia.R.id.itemIcon)
    private ImageView icon;

    @InjectView(R.id.buyNowButton)
    private Button buyNowButton;

    @InjectView(org.eurekapp.bibliopedia.R.id.firstDivider)
    @Nullable
    private View divider;

    @InjectView(R.id.readNowButton)
    private Button downloadButton;

    @InjectView(R.id.addToLibraryButton)
    private Button addToLibraryButton;

    @Inject
    private Configuration config;

    @Inject
    private NotificationManager notificationManager;

    private Feed feed;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(org.eurekapp.bibliopedia.R.layout.catalog_download, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        DisplayMetrics metrics = metricsProvider.get();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if ( this.feed != null ) {
            doSetFeed(feed);
        }
    }

    private void doSetFeed(Feed feed) {
        //If we're here, the feed always has just 1 entry
        final Entry entry = feed.getEntries().get(0);

        Option<Link> epubLink = entry.getEpubLink();

        if ( ! isEmpty(epubLink) ) {

            String base = feed.getURL();

            try {
                final URL url = new URL(new URL(base), epubLink.unsafeGet().getHref());

                downloadButton.setOnClickListener( v -> startDownload(true, url.toExternalForm() ));
                addToLibraryButton.setOnClickListener( v -> startDownload(false, url.toExternalForm()));

            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }

        } else {
            downloadButton.setVisibility(View.GONE);
            addToLibraryButton.setVisibility(View.GONE);
        }

        Option<Link> buyLink = entry.getBuyLink();

//        //no buy
//        if ( !isEmpty( buyLink ) ) {
//            buyNowButton.setOnClickListener( v -> {
//                String url = buyLink.unsafeGet().getHref();
//                Intent i = new Intent(Intent.ACTION_VIEW);
//                i.setData(Uri.parse(url));
//                startActivity(i);
//            });
//        } else {
            buyNowButton.setVisibility(View.GONE);

            if ( divider != null ) {
                divider.setVisibility(View.GONE);
            }
//        }

        if (entry.getAuthor() != null) {
            String authorText = String.format(
                    getString(org.eurekapp.bibliopedia.R.string.book_by), entry.getAuthor()
                    .getName());
            authorTextView.setText(authorText);
        } else {
            authorTextView.setText("");
        }

        final Option<Link> imgLink = Catalog.getImageLink(feed, entry);

        Catalog.loadBookDetails(mainLayout, entry, false);
        icon.setImageDrawable( getActivity().getResources().getDrawable(org.eurekapp.bibliopedia.R.drawable.unknown_cover));

        LoadThumbnailTask task = this.loadThumbnailTaskProvider.get();
        task.setLoadFeedCallback(this);
        task.setBaseUrl(feed.getURL());

        imgLink.forEach( task::execute );
    }

    @Override
    public void setNewFeed(Feed feed, ResultType resultType) {
        this.feed = feed;
        if ( this.downloadButton != null ) {
            doSetFeed(feed);
        }
    }

    @Override
    public void errorLoadingFeed(String error) {
        Toast.makeText(getActivity(), error, Toast.LENGTH_LONG ).show();
    }

    @Override
    public void emptyFeedLoaded(Feed feed) {
        errorLoadingFeed( getActivity().getString(org.eurekapp.bibliopedia.R.string.empty_opds_feed) );
    }

    private void setSupportProgressBarIndeterminateVisibility(boolean enable) {
        SherlockFragmentActivity activity = getSherlockActivity();
        if ( activity != null) {
            activity.setSupportProgressBarIndeterminateVisibility(enable);
        }
    }

    public void notifyLinkUpdated( Link link, Drawable drawable ) {

        if ( drawable != null ) {
            icon.setImageDrawable(drawable);
        }

       onLoadingDone();
    }

    @Override
    public void onLoadingStart() {
        setSupportProgressBarIndeterminateVisibility(true);
    }

    private void onLoadingDone() {
        setSupportProgressBarIndeterminateVisibility(false);
    }

    public void startDownload(final boolean openOnCompletion, final String url) {

        if ( feed == null || feed.getEntries() == null || feed.getEntries().size() == 0 ) {
            return;
        }




        final DownloadFileTask task = this.downloadFileTaskProvider.get();

        final Entry entry = feed.getEntries().get(0);
        String title = entry.getTitle();
        String author = entry.getAuthor().getName();


        //http://10.7.77.221:8080/woop4/webresources/entities.borrowed/borrow/feli/libro1autor1
        //borrow


        if ( !openOnCompletion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH ) {
            task.setCallBack( new NotificationBarCallback(getActivity().getBaseContext(), title, openOnCompletion) );
        } else {
            task.setCallBack( new ProgressDialogCallback(getActivity().getBaseContext(), task, openOnCompletion) );
        }

        task.execute(url);

    }

    private abstract class AbstractDownloadCallback implements DownloadFileTask.DownloadFileCallback {

        private boolean openOnCompletion;
        private Context context;

        public AbstractDownloadCallback( Context context, boolean openOnCompletion ) {
            this.openOnCompletion = openOnCompletion;
            this.context = context;
        }

        @Override
        public void downloadSuccess(File destFile) {

            if ( ! isAdded() ) {
                return;
            }

            if ( openOnCompletion ) {
                Intent intent = getBookOpenIntent(destFile);
                startActivity(intent);
                getActivity().finish();
            }
        }

        protected Intent getBookOpenIntent(File destFile) {
            Intent intent;

            intent = new Intent(context,
                    ReadingActivity.class);
            config.setLastActivity( ReadingActivity.class );

            intent.setData(Uri.parse(destFile.getAbsolutePath()));

            return intent;
        }

        public boolean isOpenOnCompletion() {
            return openOnCompletion;
        }

    }

    private class NotificationBarCallback extends AbstractDownloadCallback {

        private NotificationCompat.Builder builder;

        final private String title;

        final private String downloadSubtitle;
        final private String downloadSuccess;
        final private String downloadFailed;

        int notificationId;

        private Context context;

        public NotificationBarCallback(Context context, String title,
                                       boolean openOnCompletion) {
            super( context, openOnCompletion );
            this.title = title;
            this.downloadSubtitle = getString(R.string.downloading);
            this.downloadSuccess = getString(R.string.download_complete);
            this.downloadFailed = getString(org.eurekapp.bibliopedia.R.string.book_failed);

            this.notificationId = new Random().nextInt();

            this.context = context;
        }

        @Override
        public void onDownloadStart() {

            builder = new NotificationCompat.Builder(context);
            builder.setContentTitle(title)
                    .setContentText(downloadSubtitle)
                    .setSmallIcon(org.eurekapp.bibliopedia.R.drawable.download);
            builder.setTicker(downloadSubtitle);

            builder.setProgress( 0, 0, true);

            notificationManager.notify(notificationId, builder.build());

        }

        @Override
        public void progressUpdate(long progress, long total, int percentage) {

            builder.setProgress( 100, percentage, false);
            // Displays the progress bar for the first time.
            notificationManager.notify(notificationId, builder.build());

        }

        @Override
        public void downloadSuccess(File destFile) {

            builder.setContentText(downloadSuccess)
                    // Removes the progress bar
                    .setProgress(0, 0, false);

            PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                    getBookOpenIntent(destFile), 0);
            builder.setContentIntent( contentIntent );
            builder.setTicker(downloadSuccess);
            builder.setAutoCancel(true);

            notificationManager.notify(notificationId, builder.build());

            super.downloadSuccess(destFile);
        }

        @Override
        public void downloadFailed() {

            builder.setContentText(downloadFailed)
                    // Removes the progress bar
                    .setProgress(0, 0, false);
            builder.setAutoCancel(true);

            notificationManager.notify(notificationId, builder.build());

        }
    }

    private class ProgressDialogCallback extends AbstractDownloadCallback implements
        DialogInterface.OnCancelListener {

        private ProgressDialog downloadDialog;
        private DownloadFileTask task;

        private ProgressDialogCallback(Context context, DownloadFileTask task, boolean openOnCompletion) {

            super( context, openOnCompletion );

            this.downloadDialog = new ProgressDialog(getActivity());
            this.task = task;

            downloadDialog.setIndeterminate(false);
            downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            downloadDialog.setCancelable(true);


            downloadDialog.setOnCancelListener( this );
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            task.cancel(true);
        }

        @Override
        public void onDownloadStart() {
            downloadDialog.setMessage(getString(org.eurekapp.bibliopedia.R.string.downloading));
            downloadDialog.show();
        }

        @Override
        public void progressUpdate(long progress, long total, int percentage) {
            downloadDialog.setMax( Long.valueOf(total).intValue() );
            downloadDialog.setProgress(Long.valueOf(progress).intValue());
        }

        @Override
        public void downloadSuccess(File destFile) {
            downloadDialog.dismiss();

            super.downloadSuccess(destFile);

            if ( ! isOpenOnCompletion() ) {
                Toast.makeText(getActivity(), org.eurekapp.bibliopedia.R.string.download_complete,
                        Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void downloadFailed() {

            downloadDialog.dismiss();

            if ( isAdded() ) {
                Toast.makeText(getActivity(), R.string.book_failed,
                        Toast.LENGTH_LONG).show();
            }
        }

    }


}
