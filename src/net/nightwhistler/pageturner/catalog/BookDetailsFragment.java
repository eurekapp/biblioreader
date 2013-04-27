package net.nightwhistler.pageturner.catalog;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
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
import net.nightwhistler.nucular.atom.Entry;
import net.nightwhistler.nucular.atom.Feed;
import net.nightwhistler.nucular.atom.Link;
import net.nightwhistler.pageturner.R;
import net.nightwhistler.pageturner.activity.ReadingActivity;
import roboguice.inject.InjectView;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Fragment which shows the details of the book to be downloaded.
 */
public class BookDetailsFragment extends RoboSherlockFragment implements LoadFeedCallback {


    @Inject
    private Provider<LoadFakeFeedTask> loadFakeFeedTaskProvider;

    @Inject
    private Provider<DownloadFileTask> downloadFileTaskProvider;

    @Inject
    private Provider<DisplayMetrics> metricsProvider;

    @InjectView(R.id.mainLayout)
    private View mainLayout;

    @InjectView(R.id.itemAuthor)
    private TextView authorTextView;

    @InjectView(R.id.itemIcon)
    private ImageView icon;

    @InjectView(R.id.buyNowButton)
    private Button buyNowButton;

    @InjectView(R.id.readNowButton)
    private Button downloadButton;

    @InjectView(R.id.addToLibraryButton)
    private Button addToLibraryButton;

    @InjectView(R.id.relatedLinksContainer)
    ViewGroup altLinkParent;

    private int displayDensity;

    private ProgressDialog downloadDialog;

    private LinkListener linkListener;

    private Feed feed;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.catalog_download, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        DisplayMetrics metrics = metricsProvider.get();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

        this.displayDensity = metrics.densityDpi;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.downloadDialog = new ProgressDialog(getActivity());

        this.downloadDialog.setIndeterminate(false);
        this.downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        this.downloadDialog.setCancelable(true);

        if ( this.feed != null ) {
            doSetFeed(feed);
        }
    }

    private void doSetFeed(Feed feed) {
        //If we're here, the feed always has just 1 entry
        final Entry entry = feed.getEntries().get(0);

        if ( entry.getEpubLink() != null ) {

            String base = feed.getURL();

            try {
                final URL url = new URL(new URL(base), entry.getEpubLink().getHref());

                downloadButton.setOnClickListener( new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        startDownload(true, url.toExternalForm());
                    }
                });

                addToLibraryButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startDownload(false, url.toExternalForm());
                    }
                });

            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }

        } else {
            downloadButton.setVisibility(View.GONE);
            addToLibraryButton.setVisibility(View.GONE);
        }

        if (entry.getBuyLink() != null) {

            buyNowButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String url = entry.getBuyLink().getHref();
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                }
            });
        } else {
            buyNowButton.setVisibility(View.GONE);
        }


        if (entry.getAuthor() != null) {
            String authorText = String.format(
                    getString(R.string.book_by), entry.getAuthor()
                    .getName());
            authorTextView.setText(authorText);
        } else {
            authorTextView.setText("");
        }

        /*
        altLinkParent.removeAllViews();

        for ( final Link altLink: entry.getAlternateLinks() ) {
            TextView linkTextView = new TextView(getActivity());
            linkTextView.setTextAppearance( getActivity(), android.R.style.TextAppearance_Medium );
            linkTextView.setText( altLink.getTitle() );
            linkTextView.setBackgroundResource(android.R.drawable.list_selector_background );
            linkTextView.setTextColor(R.color.abs__bright_foreground_holo_light);

            linkTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ((CatalogParent) getActivity()).loadFeedFromUrl(altLink.getHref());
                }
            } );

            altLinkParent.addView(linkTextView);
        }
        */

        final Link imgLink = Catalog.getImageLink(feed, entry);

        Catalog.loadBookDetails(getActivity(), mainLayout, entry, imgLink, false, altLinkParent.getWidth() / 2);

        linkListener = new LinkListener() {

            @Override
            public void linkUpdated() {
                if ( imgLink != null ) {
                    Catalog.loadImageLink(getActivity(), icon, imgLink, altLinkParent.getWidth() / 2);
                    imgLink.setBinData(null); //Clear data, we no longer need it
                }
            }
        };

        LoadFakeFeedTask task = this.loadFakeFeedTaskProvider.get();
        task.setCallback(this);
        task.setBaseURL(feed.getURL());

        task.execute(imgLink);
    }

    @Override
    public void setNewFeed(Feed feed, ResultType resultType) {
        this.feed = feed;
        if ( this.downloadButton != null ) {
            doSetFeed(feed);
        }
    }

    @Override
    public void onStop() {
        downloadDialog.dismiss();

        super.onStop();
    }


    @Override
    public void errorLoadingFeed(String error) {
        Toast.makeText(getActivity(), error, Toast.LENGTH_LONG ).show();
    }

    private void setSupportProgressBarIndeterminateVisibility(boolean enable) {
        SherlockFragmentActivity activity = getSherlockActivity();
        if ( activity != null) {
            activity.setSupportProgressBarIndeterminateVisibility(enable);
        }
    }

    public void notifyLinkUpdated() {
        if ( linkListener != null ) {
            linkListener.linkUpdated();
            linkListener = null;
        }
    }

    @Override
    public void onLoadingStart() {
        setSupportProgressBarIndeterminateVisibility(true);
    }

    @Override
    public void onLoadingDone() {
        setSupportProgressBarIndeterminateVisibility(false);
    }

    public void startDownload(final boolean openOnCompletion, final String url) {

        DownloadFileTask.DownloadFileCallback callBack = new DownloadFileTask.DownloadFileCallback() {

            @Override
            public void onDownloadStart() {
                downloadDialog.setMessage(getString(R.string.downloading));
                downloadDialog.show();
            }

            @Override
            public void progressUpdate(long progress, long total, int percentage) {
                downloadDialog.setMax( Long.valueOf(total).intValue() );
                downloadDialog.setProgress(Long.valueOf(progress).intValue());
            }

            @Override
            public void downloadSuccess(File destFile) {

                downloadDialog.hide();

                if ( openOnCompletion ) {
                    Intent intent;

                    intent = new Intent(getActivity().getBaseContext(),
                            ReadingActivity.class);
                    intent.setData(Uri.parse(destFile.getAbsolutePath()));

                    startActivity(intent);
                    getActivity().finish();
                } else {
                    Toast.makeText(getActivity(), R.string.download_complete,
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void downloadFailed() {

                downloadDialog.hide();

                Toast.makeText(getActivity(), R.string.book_failed,
                        Toast.LENGTH_LONG).show();
            }
        };

        final DownloadFileTask task = this.downloadFileTaskProvider.get();

        DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                task.cancel(true);
            }
        };

        downloadDialog.setOnCancelListener(cancelListener);

        task.setCallBack(callBack);
        task.execute(url);

    }


}