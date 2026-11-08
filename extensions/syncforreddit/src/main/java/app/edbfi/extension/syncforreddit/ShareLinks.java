// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Opens Reddit share links ({@code reddit.com/r/<sub>/s/<id>}). The app reads the share ID as a post ID; Reddit
 * answers the share link with a redirect to the real post.
 */
public final class ShareLinks {
    private static final Pattern SHARE_LINK =
            Pattern.compile("^https?://(?:[a-z]+\\.)?reddit\\.com/(?:r|u|user)/[^/?#]+/s/[A-Za-z0-9]+/?(?:[?#].*)?$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern REDDIT_URL =
            Pattern.compile("^https://(?:[a-z]+\\.)?reddit\\.com/.*", Pattern.CASE_INSENSITIVE);
    private static final String LINK_HANDLER = "com.laurencedawson.reddit_sync.ui.activities.IntentActivity";

    // A share link that failed to resolve, handed back to the app once so it is not resolved again.
    private static String passThrough;

    private ShareLinks() {}

    /**
     * Called first by the app's link router. Returns true when it takes over the link: it then resolves the
     * redirect off the main thread and opens the result through the app's link handler.
     */
    public static synchronized boolean open(Context context, String url, String userAgent) {
        if (url == null || !isShareLink(url)) return false;
        if (url.equals(passThrough)) {
            passThrough = null;
            return false;
        }
        Context app = context.getApplicationContext();
        boolean fromActivity = context instanceof Activity;
        new Thread(() -> {
            String target = null;
            try {
                target = resolve(url, userAgent);
            } catch (Exception error) {
                Log.w(VolleyHook.TAG, "Could not resolve " + url, error);
            }
            String opened = target;
            new Handler(Looper.getMainLooper()).post(() -> openInApp(fromActivity ? context : app, url, opened));
        }, "edbfi-share-link").start();
        return true;
    }

    static boolean isShareLink(String url) {
        return SHARE_LINK.matcher(url).matches();
    }

    private static String resolve(String url, String userAgent) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        Http.Reply reply = Http.send("GET", url.replaceFirst("(?i)^http:", "https:"), headers, null, false);
        String location = reply.header("Location");
        if (reply.status / 100 != 3 || location == null) return null;
        String target = URI.create(url).resolve(location).toString();
        return REDDIT_URL.matcher(target).matches() && !isShareLink(target) ? withoutTracking(target) : null;
    }

    /** Drops the share and campaign parameters Reddit adds to the redirect target. */
    static String withoutTracking(String url) {
        int fragmentAt = url.indexOf('#');
        String fragment = fragmentAt >= 0 ? url.substring(fragmentAt) : "";
        String rest = fragmentAt >= 0 ? url.substring(0, fragmentAt) : url;
        int queryAt = rest.indexOf('?');
        if (queryAt < 0) return url;
        StringBuilder kept = new StringBuilder(rest.substring(0, queryAt));
        char separator = '?';
        for (String parameter : rest.substring(queryAt + 1).split("&")) {
            String name = parameter.split("=", 2)[0];
            if (parameter.isEmpty() || name.equals("share_id") || name.startsWith("utm_")) continue;
            kept.append(separator).append(parameter);
            separator = '&';
        }
        return kept.append(fragment).toString();
    }

    private static void openInApp(Context context, String shareLink, String target) {
        String url = target;
        if (url == null) {
            Toast.makeText(context, "Could not open the share link", Toast.LENGTH_SHORT).show();
            synchronized (ShareLinks.class) {
                passThrough = shareLink;
            }
            url = shareLink;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClassName(context, LINK_HANDLER);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
