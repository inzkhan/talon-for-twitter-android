package com.klinker.android.twitter_l.ui.search;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.klinker.android.twitter_l.R;
import com.klinker.android.twitter_l.adapters.ArrayListLoader;
import com.klinker.android.twitter_l.adapters.TimeLineCursorAdapter;
import com.klinker.android.twitter_l.adapters.TimelineArrayAdapter;
import com.klinker.android.twitter_l.data.App;
import com.klinker.android.twitter_l.manipulations.widgets.swipe_refresh_layout.FullScreenSwipeRefreshLayout;
import com.klinker.android.twitter_l.manipulations.widgets.swipe_refresh_layout.SwipeProgressBar;
import com.klinker.android.twitter_l.settings.AppSettings;
import com.klinker.android.twitter_l.ui.MainActivity;
import com.klinker.android.twitter_l.utils.Expandable;
import com.klinker.android.twitter_l.utils.ExpansionViewHelper;
import com.klinker.android.twitter_l.utils.Utils;

import org.lucasr.smoothie.AsyncListView;
import org.lucasr.smoothie.ItemManager;

import java.util.ArrayList;

import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.User;
import uk.co.senab.bitmapcache.BitmapLruCache;

public class TwitterSearchFragment extends Fragment implements Expandable {

    private AsyncListView listView;
    private LinearLayout spinner;

    private Context context;
    private AppSettings settings;

    private boolean translucent;

    public String searchQuery;
    public boolean onlyStatus;

    private FullScreenSwipeRefreshLayout mPullToRefreshLayout;

    public TwitterSearchFragment(boolean onlyStatus, String query, boolean translucent) {
        this.translucent = translucent;
        this.searchQuery = query;
        this.onlyStatus = onlyStatus;
    }

    public TwitterSearchFragment() {
        this.translucent = false;
        this.searchQuery = "";
        this.onlyStatus = false;
    }

    public boolean topTweets = false;

    private BroadcastReceiver newSearch = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            searchQuery = intent.getStringExtra("query");

            if (searchQuery.contains(" TOP")) {
                topTweets = true;
                searchQuery = searchQuery.replace(" TOP", "");
            } else {
                topTweets = false;
            }

            doSearch(searchQuery);
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.klinker.android.twitter.NEW_SEARCH");
        context.registerReceiver(newSearch, filter);
    }

    @Override
    public void onPause() {
        context.unregisterReceiver(newSearch);
        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        context = activity;
    }

    public View layout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, null);

        settings = AppSettings.getInstance(context);

        inflater = LayoutInflater.from(context);
        layout = inflater.inflate(R.layout.ptr_list_layout, null);

        mPullToRefreshLayout = (FullScreenSwipeRefreshLayout) layout.findViewById(R.id.swipe_refresh_layout);
        mPullToRefreshLayout.setFullScreen(false);
        mPullToRefreshLayout.setOnRefreshListener(new FullScreenSwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                onRefreshStarted();
            }
        });

        mPullToRefreshLayout.setColorScheme(settings.themeColors.primaryColor,
                SwipeProgressBar.COLOR2,
                settings.themeColors.primaryColorLight,
                SwipeProgressBar.COLOR3);

        listView = (AsyncListView) layout.findViewById(R.id.listView);

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {

            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                final int lastItem = firstVisibleItem + visibleItemCount;

                if(lastItem == totalItemCount && canRefresh) {
                    getMore();
                }
            }
        });

        if (translucent) {
            if (Utils.hasNavBar(context)) {
                View footer = new View(context);
                footer.setOnClickListener(null);
                footer.setOnLongClickListener(null);
                ListView.LayoutParams params = new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, Utils.getNavBarHeight(context));
                footer.setLayoutParams(params);
                listView.addFooterView(footer);
                listView.setFooterDividersEnabled(false);
            }
        }

        spinner = (LinearLayout) layout.findViewById(R.id.list_progress);
        spinner.setVisibility(View.GONE);

        if (searchQuery != null && !searchQuery.equals("")) {
            BitmapLruCache cache = App.getInstance(context).getBitmapCache();
            ArrayListLoader loader = new ArrayListLoader(cache, context);

            ItemManager.Builder builder = new ItemManager.Builder(loader);
            builder.setPreloadItemsEnabled(true).setPreloadItemsCount(10);
            builder.setThreadPoolSize(2);

            listView.setItemManager(builder.build());
        }

        if (onlyStatus) {
            try {
                findStatus(Long.parseLong(searchQuery));
            } catch (Exception e) {

            }
        } else {
            doSearch(searchQuery);
        }

        return layout;
    }

    public void onRefreshStarted() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long topId;
                if (tweets.size() > 0) {
                    topId = tweets.get(0).getId();
                } else {
                    topId = 0;
                }

                try {
                    Twitter twitter = Utils.getTwitter(context, settings);
                    query = new Query(searchQuery);
                    if (topTweets) {
                        query.setResultType(Query.POPULAR);
                    } else {
                        query.setResultType(null);
                    }
                    QueryResult result = twitter.search(query);

                    tweets.clear();

                    for (twitter4j.Status status : result.getTweets()) {
                        tweets.add(status);
                    }

                    if (result.hasNext()) {
                        query = result.nextQuery();
                        hasMore = true;
                    } else {
                        hasMore = false;
                    }

                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int top = 0;
                            for (int i = 0; i < tweets.size(); i++) {
                                if (tweets.get(i).getId() == topId) {
                                    top = i;
                                    break;
                                }
                            }

                            adapter = new TimelineArrayAdapter(context, tweets, onlyStatus);
                            listView.setAdapter(adapter);
                            listView.setVisibility(View.VISIBLE);
                            listView.setSelection(top);

                            spinner.setVisibility(View.GONE);

                            mPullToRefreshLayout.setRefreshing(false);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            spinner.setVisibility(View.GONE);
                            mPullToRefreshLayout.setRefreshing(false);
                        }
                    });
                }
            }
        }).start();
    }

    public void doSearch(final String mQuery) {
        spinner.setVisibility(View.VISIBLE);

        if (listView.getVisibility() != View.GONE) {
            listView.setVisibility(View.GONE);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Twitter twitter = Utils.getTwitter(context, settings);
                    Log.v("talon_searching", "query in frag: " + mQuery);
                    query = new Query(mQuery);
                    if (topTweets) {
                        query.setResultType(Query.ResultType.popular);
                    } else {
                        query.setResultType(null);
                    }
                    QueryResult result = twitter.search(query);

                    tweets.clear();

                    for (twitter4j.Status status : result.getTweets()) {
                        tweets.add(status);
                    }

                    if (result.hasNext()) {
                        query = result.nextQuery();
                        hasMore = true;
                    } else {
                        hasMore = false;
                    }

                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter = new TimelineArrayAdapter(context, tweets, onlyStatus);
                            listView.setAdapter(adapter);
                            listView.setVisibility(View.VISIBLE);

                            spinner.setVisibility(View.GONE);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            spinner.setVisibility(View.GONE);
                        }
                    });
                } catch (OutOfMemoryError e) {
                    e.printStackTrace();
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            spinner.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }).start();
    }

    public void findStatus(final long statusid) {
        listView.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        hasMore = false;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Twitter twitter = Utils.getTwitter(context, settings);
                    Status status = twitter.showStatus(statusid);

                    final ArrayList<Status> statuses = new ArrayList<Status>();

                    statuses.add(status);

                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TimelineArrayAdapter adapter = new TimelineArrayAdapter(context, statuses, onlyStatus);
                            listView.setAdapter(adapter);
                            listView.setVisibility(View.VISIBLE);
                            spinner.setVisibility(View.GONE);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();

                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            spinner.setVisibility(View.GONE);
                        }
                    });
                    hasMore = false;
                }
            }
        }).start();
    }

    public int toDP(int px) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px, getResources().getDisplayMetrics());
    }

    public boolean canRefresh = true;
    public boolean hasMore;
    public TimelineArrayAdapter adapter;
    public Query query;
    public ArrayList<Status> tweets = new ArrayList<Status>();

    public void getMore() {
        if (hasMore) {
            canRefresh = false;
            mPullToRefreshLayout.setRefreshing(true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Twitter twitter = Utils.getTwitter(context, settings);
                        QueryResult result = twitter.search(query);

                        for (twitter4j.Status status : result.getTweets()) {
                            tweets.add(status);
                        }

                        if (result.hasNext()) {
                            query = result.nextQuery();
                            hasMore = true;
                        } else {
                            hasMore = false;
                        }

                        ((Activity)context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                adapter.notifyDataSetChanged();
                                mPullToRefreshLayout.setRefreshing(false);
                                canRefresh = true;
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        ((Activity)context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mPullToRefreshLayout.setRefreshing(false);
                                canRefresh = true;
                            }
                        });
                    }
                }
            }).start();
        }
    }

    private int expandedDistanceFromTop = 0;

    @Override
    public void expandViewOpen(final int distanceFromTop, int position, View root, ExpansionViewHelper helper) {
        expandedDistanceFromTop = distanceFromTop;

        listView.smoothScrollBy(distanceFromTop - Utils.getActionBarHeight(context) + Utils.getStatusBarHeight(context), TimeLineCursorAdapter.ANIMATION_DURATION);
    }

    @Override
    public void expandViewClosed(int currentDistanceFromTop) {

        if (currentDistanceFromTop != -1) {
            listView.smoothScrollBy(-1 * expandedDistanceFromTop + currentDistanceFromTop, TimeLineCursorAdapter.ANIMATION_DURATION);
        }
    }
}