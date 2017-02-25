
package chatty.util.api;

import chatty.util.api.UserIDs.UserIdResult;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles TwitchApi requests and responses.
 * 
 * @author tduva
 */
public class TwitchApi {

    private final static Logger LOGGER = Logger.getLogger(TwitchApi.class.getName());

    /**
     * How long the Emoticons can be cached in a file after they are updated
     * from the API.
     */
    public static final int CACHED_EMOTICONS_EXPIRE_AFTER = 60*60*24;
    
    public static final int TOKEN_CHECK_DELAY = 600;

    
    
    public enum RequestResultCode {
        ACCESS_DENIED, SUCCESS, FAILED, NOT_FOUND, RUNNING_COMMERCIAL,
        INVALID_CHANNEL, INVALID_STREAM_STATUS, UNKNOWN
    }
    
    private final TwitchApiResultListener resultListener;
    
    protected final StreamInfoManager streamInfoManager;
    protected final EmoticonManager emoticonManager;
    protected final CheerEmoticonManager cheersManager;
    protected final FollowerManager followerManager;
    protected final FollowerManager subscriberManager;
    protected final BadgeManager badgeManager;
    protected final UserIDs userIDs;
    protected final ChannelInfoManager channelInfoManager;
    
    private volatile Long tokenLastChecked = Long.valueOf(0);
    
    private volatile String defaultToken;

    protected final Requests requests;

    public TwitchApi(TwitchApiResultListener apiResultListener,
            StreamInfoListener streamInfoListener) {
        this.resultListener = apiResultListener;
        this.streamInfoManager = new StreamInfoManager(this, streamInfoListener);
        emoticonManager = new EmoticonManager(apiResultListener);
        cheersManager = new CheerEmoticonManager(apiResultListener);
        followerManager = new FollowerManager(Follower.Type.FOLLOWER, this, resultListener);
        subscriberManager = new FollowerManager(Follower.Type.SUBSCRIBER, this, resultListener);
        badgeManager = new BadgeManager(this);
        requests = new Requests(this, resultListener);
        channelInfoManager = new ChannelInfoManager(this, resultListener);
        userIDs = new UserIDs(this);
    }
    
    
    //=================
    // Chat / Emoticons
    //=================
    
    public void requestCheerEmoticons(boolean forcedUpdate) {
        if (forcedUpdate || !cheersManager.load(false)) {
            requests.requestCheerEmoticons(forcedUpdate);
        }
    }
    
    public void requestEmoticons(boolean forcedUpdate) {
        if (forcedUpdate || !emoticonManager.load(false)) {
            requests.requestEmoticons(forcedUpdate);
        }
    }
    
    public void getGlobalBadges(boolean forceRefresh) {
        badgeManager.requestGlobalBadges(forceRefresh);
    }
    
    public void getRoomBadges(String room, boolean forceRefresh) {
        badgeManager.requestBadges(room, forceRefresh);
    }
    
    //====================
    // Channel Information
    //====================
    
    public void getChannelInfo(String stream) {
        requests.getChannelInfo(stream);
    }

    public void getChatInfo(String stream) {
        requests.requestChatInfo(stream);
    }
    
    public void getFollowers(String stream) {
        followerManager.request(stream);
    }
    
    public void getSubscribers(String stream) {
        subscriberManager.request(stream);
    }
    
    public ChannelInfo getOnlyCachedChannelInfo(String stream) {
        return channelInfoManager.getOnlyCachedChannelInfo(stream);
    }
    
    public ChannelInfo getCachedChannelInfo(String stream) {
        return channelInfoManager.getCachedChannelInfo(stream);
    }
    
    //===================
    // Stream Information
    //===================
    
    /**
     * Get StreamInfo for the given stream. Always returns a StreamInfo object,
     * which may however be marked as invalid if the stream is no valid stream
     * name or does not exist or data hasn't been requested yet.
     *
     * The first request per stream is always invalid, because the info has
     * to be requested from the server first. Further request return a cached
     * version of the StreamInfo, until the info is marked as expired.
     * 
     * @param stream
     * @return The StreamInfo object
     */
    public StreamInfo getStreamInfo(String stream, Set<String> streams) {
        if (streams == null) {
            streams = new HashSet<>();
        }
        return streamInfoManager.getStreamInfo(stream, streams);
    }
    
    public void getFollowedStreams(String token) {
        streamInfoManager.getFollowedStreams(token);
    }
    
    public void manualRefreshStreams() {
        streamInfoManager.manualRefresh();
    }

    //======
    // Token
    //======
    
    public void setToken(String token) {
        this.defaultToken = token;
    }
    
    /**
     * When access was denied when doing an authenticated request. Check the
     * token maybe subsequently.
     */
    protected void accessDenied() {
        resultListener.accessDenied();
    }
    
    /**
     * Verifies token, but only once the delay has passed. For automatic checks
     * instead of manual ones.
     * 
     * @param token 
     */
    public void checkToken(String token) {
        if (token != null && !token.isEmpty() &&
                (System.currentTimeMillis() - tokenLastChecked) / 1000 > TOKEN_CHECK_DELAY) {
            LOGGER.info("Checking token..");
            tokenLastChecked = Long.valueOf(System.currentTimeMillis());
            requests.verifyToken(token);
        }
    }
    
    public void verifyToken(String token) {
        requests.verifyToken(token);
    }
    
    public String getToken() {
        return defaultToken;
    }
    
    
    //=========
    // User IDs
    //=========

    public void setUserId(String userName, String userId) {
        userIDs.setUserId(userName, userId);
    }
    
    public void waitForUserId(UserIDs.UserIdResultListener listener, String... names) {
        userIDs.waitForUserIDs(listener, names);
    }
    
    public void getUserId(UserIDs.UserIdResultListener listener, String... names) {
        userIDs.getUserIDsAsap(listener, names);
    }
    
    public void requestUserId(String... names) {
        userIDs.requestUserIDs(names);
    }
    
    public void getUserIDsTest(String usernames) {
        userIDs.getUserIDsAsap(r -> {
            System.out.println(r.getValidIDs());
        }, usernames.split(" "));
    }
    
    public void getUserIDsTest2(String usernames) {
        UserIdResult result = userIDs.requestUserIDs(usernames.split(" "));
        if (result != null) {
            System.out.println(result.getValidIDs());
        }
    }
    
    public void getUserIDsTest3(String usernames) {
        userIDs.waitForUserIDs(r -> {
            System.out.println(r.getValidIDs());
        }, usernames.split(" "));
    }
    
    //================
    // User Management
    //================
    
    public void followChannel(String user, String target) {
        userIDs.getUserIDsAsap(r -> {
            if (r.hasError()) {
                resultListener.followResult("Couldn't follow '" + target + "' ("+r.getError()+")");
            } else {
                requests.followChannel(r.getId(user), r.getId(target), target, defaultToken);
            }
        }, user, target);
    }
    
    public void unfollowChannel(String user, String target) {
        userIDs.getUserIDsAsap(r -> {
            if (r.hasError()) {
                resultListener.followResult("Couldn't unfollow '" + target + "' ("+r.getError()+")");
            } else {
                requests.unfollowChannel(r.getId(user), r.getId(target), target, defaultToken);
            }
        }, user, target);
    }
    
    
    //===================
    // Admin / Moderation
    //===================
    
    public void putChannelInfo(ChannelInfo info) {
        requests.putChannelInfo(info, defaultToken);
    }
    
    public void performGameSearch(String search) {
        requests.getGameSearch(search);
    }
    
    public void runCommercial(String stream, int length) {
        requests.runCommercial(stream, defaultToken, length);
    }
    
    public void autoMod(String action, String msgId) {
        requests.autoMod(action, msgId, defaultToken);
    }

}