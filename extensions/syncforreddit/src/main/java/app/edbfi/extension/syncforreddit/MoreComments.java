// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import android.util.Log;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Answers the app's {@code POST /api/morechildren}, which Reddit refuses this client (403), from the comment pages
 * {@code /comments/<post>/_/<comment>}, which it still serves.
 *
 * <p>The answer has the endpoint's own shape: the requested comments with their replies as one flat list, each
 * after its parent and with empty {@code replies}. The app takes a row's level from its parent in the list, and moves
 * the rows below the tapped one down by the list's length, so the list must not nest.
 */
public final class MoreComments {
    private static final String API = "https://oauth.reddit.com/";
    private static final String ENDPOINT = API + "api/morechildren";
    // As in the app's own comment requests.
    private static final String QUERY = "?always_show_media=1&profile_img=true";
    /** Most comment pages fetched for one tap; the comments left over come back as a new "View more" row. */
    static final int BATCH = 20;
    // Comment pages read the parent's replies up to the app's own thread limit, a single comment's up to this.
    private static final int PARENT_LIMIT = 500;
    private static final int COMMENT_LIMIT = 50;
    // Requests left in Reddit's rate-limit window that are kept for the app's own requests.
    private static final int RESERVE = 20;
    private static final int THREADS = 4;

    private static ExecutorService pool;
    // X-Ratelimit-Remaining from the last comment page, or -1 before the first.
    private static volatile int remaining = -1;

    /** Where comments come from: Reddit in the app, fixtures in tests. */
    interface Source {
        /**
         * The comment as a {@code t1} thing with its replies, or null if Reddit no longer has it. Throws if the request
         * fails. {@code parent} asks for as many replies as the page holds.
         */
        JSONObject comment(String id, boolean parent) throws Exception;

        /** How many more requests may be sent now. */
        int budget();
    }

    private MoreComments() {}

    /** Turned on by the "Fix View more comments" patch. */
    public static boolean isEnabled() {
        return false;
    }

    static boolean handles(String url) {
        return url != null && url.startsWith(ENDPOINT);
    }

    /**
     * Never returns null: the app's own request would only get Reddit's 403. A failure becomes an error response,
     * after which the app leaves the "View more" row as it was.
     */
    static NetworkResponse answer(Request<?> request) {
        try {
            Map<String, String> form = form(request.getBody());
            String link = form.get("link_id");
            List<String> ids = ids(form.get("children"));
            if (link == null || !link.matches("t3_[a-z0-9]+") || ids.isEmpty()) {
                throw new IllegalArgumentException("Unexpected request body: " + form);
            }
            Source reddit = new Reddit(link.substring(3), sort(form.get("sort")), request.getHeaders());
            JSONArray things = things(link, ids, reddit, pool());
            JSONObject body = new JSONObject().put("json", new JSONObject().put("errors", new JSONArray())
                    .put("data", new JSONObject().put("things", things)));
            return reply(200, body.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            Log.w(VolleyHook.TAG, "More comments failed", error);
            return reply(502, new byte[0]);
        }
    }

    /**
     * The things to answer with, in the requested order. The first comment Reddit still has tells whose replies they
     * are; if they are a comment's, one page of that comment usually holds them all. The others are fetched one by
     * one, at most {@link #BATCH} pages in all; the rest come back as a "more" thing. Comments Reddit no longer has
     * are left out.
     */
    static JSONArray things(String link, List<String> ids, Source source, ExecutorService pool) throws Exception {
        int limit = Math.min(BATCH, Math.max(1, source.budget()));
        Map<String, JSONObject> found = new HashMap<>();
        Set<String> gone = new HashSet<>();
        int sent = 0;
        String parent = null;
        // A failure here fails the whole answer: nothing could be loaded.
        for (String id : ids) {
            if (sent >= limit) break;
            JSONObject comment = source.comment(id, false);
            sent++;
            if (comment == null) {
                gone.add(id);
                continue;
            }
            found.put(id, comment);
            parent = comment.getJSONObject("data").getString("parent_id");
            break;
        }

        if (parent != null && parent.startsWith("t1_") && sent < limit) {
            sent++;
            try {
                JSONObject page = source.comment(parent.substring(3), true);
                if (page != null) {
                    Set<String> wanted = new HashSet<>(ids);
                    for (JSONObject reply : replies(page)) {
                        String id = reply.getJSONObject("data").getString("id");
                        if ("t1".equals(reply.getString("kind")) && wanted.contains(id)) found.put(id, reply);
                    }
                }
            } catch (Exception error) {
                Log.w(VolleyHook.TAG, "Parent comment failed, loading replies one by one", error);
            }
        }

        List<String> rest = new ArrayList<>();
        for (String id : ids) {
            if (sent + rest.size() >= limit) break;
            if (!found.containsKey(id) && !gone.contains(id)) rest.add(id);
        }
        if (!rest.isEmpty()) {
            List<Callable<JSONObject>> fetches = new ArrayList<>();
            for (final String id : rest) fetches.add(() -> source.comment(id, false));
            List<Future<JSONObject>> results = pool.invokeAll(fetches);
            for (int i = 0; i < rest.size(); i++) {
                try {
                    JSONObject comment = results.get(i).get();
                    if (comment == null) gone.add(rest.get(i));
                    else found.put(rest.get(i), comment);
                } catch (ExecutionException error) {
                    // Left for the next tap.
                    Log.w(VolleyHook.TAG, "Comment " + rest.get(i) + " failed", error.getCause());
                }
            }
        }

        JSONArray things = new JSONArray();
        List<String> left = new ArrayList<>();
        for (String id : ids) {
            JSONObject comment = found.get(id);
            if (comment != null) flatten(comment, things);
            else if (!gone.contains(id)) left.add(id);
        }
        if (!left.isEmpty()) things.put(more(left, parent != null ? parent : link));
        return things;
    }

    /** The comment the page is about, or null when Reddit no longer has it and sends the post's top comments. */
    static JSONObject focus(JSONArray page, String id) throws JSONException {
        JSONArray children = page.getJSONObject(1).getJSONObject("data").getJSONArray("children");
        if (children.length() == 0) return null;
        JSONObject first = children.getJSONObject(0);
        if (!"t1".equals(first.getString("kind")) || !id.equals(first.getJSONObject("data").optString("id"))) return null;
        return first;
    }

    /** Adds the thing and, after it, every reply below it, emptying each comment's {@code replies}. */
    static void flatten(JSONObject thing, JSONArray out) throws JSONException {
        out.put(thing);
        if (!"t1".equals(thing.getString("kind"))) return;
        List<JSONObject> replies = replies(thing);
        thing.getJSONObject("data").put("replies", "");
        for (JSONObject reply : replies) flatten(reply, out);
    }

    private static List<JSONObject> replies(JSONObject comment) throws JSONException {
        List<JSONObject> replies = new ArrayList<>();
        JSONObject listing = comment.getJSONObject("data").optJSONObject("replies");
        if (listing == null) return replies;
        JSONArray children = listing.getJSONObject("data").getJSONArray("children");
        for (int i = 0; i < children.length(); i++) replies.add(children.getJSONObject(i));
        return replies;
    }

    /** A "more" thing as Reddit sends it, which the app shows as a "View more" row. */
    static JSONObject more(List<String> ids, String parent) throws JSONException {
        JSONArray children = new JSONArray();
        for (String id : ids) children.put(id);
        JSONObject data = new JSONObject()
                .put("id", ids.get(0))
                .put("name", "t1_" + ids.get(0))
                .put("parent_id", parent)
                .put("count", ids.size())
                .put("children", children);
        return new JSONObject().put("kind", "more").put("data", data);
    }

    /** The request's form fields. */
    static Map<String, String> form(byte[] body) throws UnsupportedEncodingException {
        Map<String, String> form = new HashMap<>();
        if (body == null) return form;
        for (String field : new String(body, StandardCharsets.UTF_8).split("&")) {
            int equals = field.indexOf('=');
            if (equals <= 0) continue;
            form.put(URLDecoder.decode(field.substring(0, equals), "UTF-8"),
                    URLDecoder.decode(field.substring(equals + 1), "UTF-8"));
        }
        return form;
    }

    /** The comma-separated comment IDs, without duplicates, skipping any that aren't base-36. */
    static List<String> ids(String children) {
        List<String> ids = new ArrayList<>();
        if (children == null) return ids;
        for (String id : children.split(",")) {
            String trimmed = id.trim();
            if (trimmed.matches("[a-z0-9]+") && !ids.contains(trimmed)) ids.add(trimmed);
        }
        return ids;
    }

    /** The sort for the comment pages; "suggested" is the post's own, which the app also leaves out. */
    static String sort(String sort) {
        return sort == null || !sort.matches("[a-z]+") || "suggested".equals(sort) ? null : sort;
    }

    private static synchronized ExecutorService pool() {
        if (pool == null) {
            pool = Executors.newFixedThreadPool(THREADS, task -> {
                Thread thread = new Thread(task, "edbfi-more-comments");
                thread.setDaemon(true);
                return thread;
            });
        }
        return pool;
    }

    private static NetworkResponse reply(int status, byte[] body) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Content-Type", "application/json");
        return VolleyHook.response(new Http.Reply(status, body, headers));
    }

    /** Comment pages from Reddit, sent with the app's own token and User-Agent. */
    private static final class Reddit implements Source {
        private final String post;
        private final String sort;
        private final Map<String, String> headers;

        Reddit(String post, String sort, Map<String, String> headers) {
            this.post = post;
            this.sort = sort;
            this.headers = headers;
        }

        @Override
        public JSONObject comment(String id, boolean parent) throws Exception {
            String url = API + "comments/" + post + "/_/" + id + ".json" + QUERY
                    + "&limit=" + (parent ? PARENT_LIMIT : COMMENT_LIMIT) + (sort == null ? "" : "&sort=" + sort);
            Http.Reply reply = Http.send("GET", url, headers, null, true);
            String left = reply.header("X-Ratelimit-Remaining");
            if (left != null) {
                try {
                    remaining = (int) Double.parseDouble(left);
                } catch (NumberFormatException ignored) {
                    // Keep the last value.
                }
            }
            if (reply.status != 200) throw new IOException("Reddit returned " + reply.status + " for " + url);
            return focus(new JSONArray(new String(reply.body, StandardCharsets.UTF_8)), id);
        }

        @Override
        public int budget() {
            return remaining < 0 ? BATCH : remaining - RESERVE;
        }
    }
}
