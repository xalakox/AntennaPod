package de.danoeh.antennapod.storage.database;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DbReaderNewestPerFeedTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
    }

    @Test
    public void testGetEpisodesNewestPerFeedEnabledReturnsSingleEpisodePerFeed() {
        Feed feedA = createFeed("feed-a");
        feedA.getItems().add(createItem(feedA, "feed-a-old", 1000L, FeedItem.UNPLAYED));
        feedA.getItems().add(createItem(feedA, "feed-a-new", 2000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedA, false);

        Feed feedB = createFeed("feed-b");
        feedB.getItems().add(createItem(feedB, "feed-b-old", 1500L, FeedItem.UNPLAYED));
        feedB.getItems().add(createItem(feedB, "feed-b-new", 3000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedB, false);

        List<FeedItem> grouped = DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, true);
        Set<String> identifiers = getItemIdentifiers(grouped);

        assertEquals(2, grouped.size());
        assertTrue(identifiers.contains("feed-a-new"));
        assertTrue(identifiers.contains("feed-b-new"));
        assertEquals(2, DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered(), true));
    }

    @Test
    public void testGetEpisodesNewestPerFeedDisabledReturnsAllEpisodes() {
        Feed feedA = createFeed("feed-a");
        feedA.getItems().add(createItem(feedA, "feed-a-1", 1000L, FeedItem.UNPLAYED));
        feedA.getItems().add(createItem(feedA, "feed-a-2", 2000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedA, false);

        Feed feedB = createFeed("feed-b");
        feedB.getItems().add(createItem(feedB, "feed-b-1", 3000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedB, false);

        List<FeedItem> ungrouped = DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, false);

        assertEquals(3, ungrouped.size());
        assertEquals(3, DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered(), false));
    }

    @Test
    public void testGetEpisodesNewestPerFeedRespectsNewFilter() {
        Feed feedA = createFeed("feed-a");
        feedA.getItems().add(createItem(feedA, "feed-a-new", 1000L, FeedItem.NEW));
        feedA.getItems().add(createItem(feedA, "feed-a-unplayed-newer", 2000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedA, false);

        Feed feedB = createFeed("feed-b");
        feedB.getItems().add(createItem(feedB, "feed-b-new", 1500L, FeedItem.NEW));
        FeedDatabaseWriter.updateFeed(context, feedB, false);

        Feed feedC = createFeed("feed-c");
        feedC.getItems().add(createItem(feedC, "feed-c-unplayed", 2500L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedC, false);

        FeedItemFilter newOnly = new FeedItemFilter(FeedItemFilter.NEW);
        List<FeedItem> grouped = DBReader.getEpisodes(0, 10, newOnly, SortOrder.DATE_NEW_OLD, true);
        Set<String> identifiers = getItemIdentifiers(grouped);

        assertEquals(2, grouped.size());
        assertTrue(identifiers.contains("feed-a-new"));
        assertTrue(identifiers.contains("feed-b-new"));
        assertEquals(2, DBReader.getTotalEpisodeCount(newOnly, true));
    }

    @Test
    public void testGetEpisodesNewestPerFeedUsesIdAsTieBreaker() {
        Feed feed = createFeed("feed-tie");
        feed.getItems().add(createItem(feed, "tie-item-a", 1000L, FeedItem.UNPLAYED));
        feed.getItems().add(createItem(feed, "tie-item-b", 1000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feed, false);

        List<FeedItem> ungrouped = DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, false);
        long itemAId = findItemIdByIdentifier(ungrouped, "tie-item-a");
        long itemBId = findItemIdByIdentifier(ungrouped, "tie-item-b");

        assertNotEquals(itemAId, itemBId);

        List<FeedItem> grouped = DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, true);
        assertEquals(1, grouped.size());
        assertEquals(Math.max(itemAId, itemBId), grouped.get(0).getId());
        assertEquals(1, DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered(), true));
    }

    @Test
    public void testGetEpisodesGlobalDisabledWithPerFeedEnabledOverride() {
        Feed feedA = createFeed("feed-a");
        feedA.getItems().add(createItem(feedA, "feed-a-old", 1000L, FeedItem.UNPLAYED));
        feedA.getItems().add(createItem(feedA, "feed-a-new", 2000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedA, false);

        Feed feedB = createFeed("feed-b");
        feedB.getItems().add(createItem(feedB, "feed-b-old", 1500L, FeedItem.UNPLAYED));
        feedB.getItems().add(createItem(feedB, "feed-b-new", 3000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedB, false);

        setNewestEpisodesPerFeed(feedA.getId(), FeedPreferences.NewestEpisodesPerFeed.ENABLED);

        List<FeedItem> items = DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, false);
        Set<String> identifiers = getItemIdentifiers(items);

        assertEquals(3, items.size());
        assertTrue(identifiers.contains("feed-a-new"));
        assertTrue(identifiers.contains("feed-b-new"));
        assertTrue(identifiers.contains("feed-b-old"));
        assertEquals(3, DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered(), false));
    }

    @Test
    public void testGetEpisodesGlobalEnabledWithPerFeedDisabledOverride() {
        Feed feedA = createFeed("feed-a");
        feedA.getItems().add(createItem(feedA, "feed-a-old", 1000L, FeedItem.UNPLAYED));
        feedA.getItems().add(createItem(feedA, "feed-a-new", 2000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedA, false);

        Feed feedB = createFeed("feed-b");
        feedB.getItems().add(createItem(feedB, "feed-b-old", 1500L, FeedItem.UNPLAYED));
        feedB.getItems().add(createItem(feedB, "feed-b-new", 3000L, FeedItem.UNPLAYED));
        FeedDatabaseWriter.updateFeed(context, feedB, false);

        setNewestEpisodesPerFeed(feedA.getId(), FeedPreferences.NewestEpisodesPerFeed.DISABLED);

        List<FeedItem> items = DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, true);
        Set<String> identifiers = getItemIdentifiers(items);

        assertEquals(3, items.size());
        assertTrue(identifiers.contains("feed-a-new"));
        assertTrue(identifiers.contains("feed-a-old"));
        assertTrue(identifiers.contains("feed-b-new"));
        assertEquals(3, DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered(), true));
    }

    private Feed createFeed(String slug) {
        Feed feed = new Feed("https://example.com/" + slug, null, "Feed " + slug);
        feed.setItems(new ArrayList<>());
        return feed;
    }

    private FeedItem createItem(Feed feed, String identifier, long pubDate, int state) {
        FeedItem item = new FeedItem(0, "Item " + identifier, identifier,
                "https://example.com/item/" + identifier, new Date(pubDate), state, feed);
        item.setMedia(new FeedMedia(item, "https://example.com/media/" + identifier, 2, "mime"));
        return item;
    }

    private Set<String> getItemIdentifiers(List<FeedItem> items) {
        Set<String> identifiers = new HashSet<>();
        for (FeedItem item : items) {
            identifiers.add(item.getItemIdentifier());
        }
        return identifiers;
    }

    private long findItemIdByIdentifier(List<FeedItem> items, String identifier) {
        for (FeedItem item : items) {
            if (identifier.equals(item.getItemIdentifier())) {
                return item.getId();
            }
        }
        throw new IllegalStateException("Identifier not found: " + identifier);
    }

    private void setNewestEpisodesPerFeed(long feedId, FeedPreferences.NewestEpisodesPerFeed mode) {
        Feed dbFeed = DBReader.getFeed(feedId, false, 0, 0);
        FeedPreferences preferences = dbFeed.getPreferences();
        preferences.setNewestEpisodesPerFeed(mode);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setFeedPreferences(preferences);
        adapter.close();
    }
}
