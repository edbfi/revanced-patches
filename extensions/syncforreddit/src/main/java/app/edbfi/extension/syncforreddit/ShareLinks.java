// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.net.MalformedURLException;
import java.net.URL;
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

    // Share links that failed to resolve, each handed back to the app once so it is not resolved again.
    private static final Map<String, Integer> PASS_THROUGH = new HashMap<>();

    private ShareLinks() {}

    /**
     * Called first by the app's link router. Returns true when it takes over the link: it then resolves the
     * redirect off the main thread and opens the result through the app's link handler.
     */
    public static boolean open(Context context, String url, String userAgent) {
        if (url == null || !isShareLink(url) || takePassThrough(url)) return false;
        // The caller may be an activity that finishes right away, so the result opens from the application.
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String target = null;
            try {
                target = resolve(url, userAgent);
            } catch (Exception error) {
                Log.w(VolleyHook.TAG, "Could not resolve " + url, error);
            }
            String opened = target;
            new Handler(Looper.getMainLooper()).post(() -> openInApp(app, url, opened));
        }, "edbfi-share-link").start();
        return true;
    }

    static boolean isShareLink(String url) {
        return SHARE_LINK.matcher(url).matches();
    }

    static synchronized void allowPassThrough(String url) {
        Integer pending = PASS_THROUGH.get(url);
        PASS_THROUGH.put(url, pending == null ? 1 : pending + 1);
    }

    static synchronized boolean takePassThrough(String url) {
        Integer pending = PASS_THROUGH.get(url);
        if (pending == null) return false;
        if (pending == 1) PASS_THROUGH.remove(url);
        else PASS_THROUGH.put(url, pending - 1);
        return true;
    }

    private static String resolve(String url, String userAgent) throws Exception {
        String request = url.replaceFirst("(?i)^http:", "https:");
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        Http.Reply reply = Http.send("GET", request, headers, null, false);
        return target(request, reply.status, reply.header("Location"));
    }

    /** The Reddit URL a share link's redirect points to, without tracking parameters, or null. */
    static String target(String request, int status, String location) {
        if (status / 100 != 3 || location == null) return null;
        String target;
        try {
            target = new URL(new URL(request), location).toString();
        } catch (MalformedURLException error) {
            return null;
        }
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

    private static void openInApp(Context app, String shareLink, String target) {
        String url = target;
        if (url == null) {
            Toast.makeText(app, "Could not open the share link", Toast.LENGTH_SHORT).show();
            allowPassThrough(shareLink);
            url = shareLink;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setClassName(app, LINK_HANDLER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(intent);
    }
}
