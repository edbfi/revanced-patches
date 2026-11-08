// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import android.util.Log;
import com.android.volley.NetworkResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONObject;

/**
 * Answers the app's three Redgifs requests with Redgifs' temporary-token API. The app's client credentials still
 * get a token, but it no longer returns video URLs, and the address lookup it needs first is gone (404).
 */
public final class Redgifs {
    private static final String API = "https://api.redgifs.com";
    private static final String TOKEN_URL = API + "/v2/oauth/client";
    private static final String ADDRESS_URL = API + "/info";
    private static final String GIF_URL = API + "/v2/gifs/";
    // Temporary tokens last 24 hours; renew an hour early.
    private static final long TOKEN_LIFETIME_MS = 23L * 60 * 60 * 1000;

    private static final class Session {
        final String token;
        final String address;
        final long expiresAt;

        Session(String token, String address, long expiresAt) {
            this.token = token;
            this.address = address;
            this.expiresAt = expiresAt;
        }
    }

    private static Session session;

    private Redgifs() {}

    /** Turned on by the "Fix Redgifs" patch. */
    public static boolean isEnabled() {
        return false;
    }

    static boolean handles(String url) {
        return url != null && (url.startsWith(TOKEN_URL) || url.startsWith(ADDRESS_URL) || url.startsWith(GIF_URL));
    }

    /**
     * Never returns null: the app's own requests would send Redgifs its client credentials and the User-Agent it
     * sends to Reddit. A failure becomes an error response, which the app reports as a failed load.
     */
    static NetworkResponse answer(String url) {
        try {
            if (url.startsWith(TOKEN_URL)) return json(new JSONObject().put("access_token", session(false).token));
            if (url.startsWith(ADDRESS_URL)) return json(new JSONObject().put("remote-addr", session(false).address));
            String id = gifId(url);
            if (id == null) return failure();
            Http.Reply reply = Http.send("GET", GIF_URL + id, headers(session(false).token), null, true);
            // A token is bound to the User-Agent it was issued for and may have been revoked early.
            if (reply.status == 401) reply = Http.send("GET", GIF_URL + id, headers(session(true).token), null, true);
            return VolleyHook.response(reply);
        } catch (Exception error) {
            Log.w(VolleyHook.TAG, "Redgifs request failed: " + url, error);
            return failure();
        }
    }

    /** The gif ID from the app's {@code /v2/gifs/<id>?user-addr=…} URL, lowercased as Redgifs requires, or null. */
    static String gifId(String url) {
        if (url == null || !url.startsWith(GIF_URL)) return null;
        String rest = url.substring(GIF_URL.length());
        int end = rest.length();
        for (char stop : new char[] {'?', '/', '#'}) {
            int at = rest.indexOf(stop);
            if (at >= 0 && at < end) end = at;
        }
        String id = rest.substring(0, end);
        // The app keeps the case of IDs it takes from media URLs (BothPrivatePigeon-mobile.mp4); the API answers 404.
        return id.matches("[A-Za-z0-9]+") ? id.toLowerCase(Locale.ROOT) : null;
    }

    private static synchronized Session session(boolean renew) throws Exception {
        if (renew || session == null || System.currentTimeMillis() >= session.expiresAt) {
            Http.Reply reply = Http.send("GET", API + "/v2/auth/temporary", headers(null), null, true);
            if (reply.status != 200) throw new IllegalStateException("Redgifs token request returned " + reply.status);
            JSONObject body = new JSONObject(new String(reply.body, StandardCharsets.UTF_8));
            session = new Session(
                    body.getString("token"), body.optString("addr", ""), System.currentTimeMillis() + TOKEN_LIFETIME_MS);
        }
        return session;
    }

    // Redgifs gets the system's own User-Agent, never the one the app sends to Reddit.
    private static Map<String, String> headers(String bearer) {
        Map<String, String> headers = new HashMap<>();
        String agent = System.getProperty("http.agent");
        headers.put("User-Agent", agent == null || agent.isEmpty() ? "Dalvik/2.1.0 (Linux; Android)" : agent);
        if (bearer != null) headers.put("Authorization", "Bearer " + bearer);
        return headers;
    }

    private static NetworkResponse json(JSONObject body) {
        return reply(200, body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static NetworkResponse failure() {
        return reply(502, new byte[0]);
    }

    private static NetworkResponse reply(int status, byte[] body) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Content-Type", "application/json");
        return VolleyHook.response(new Http.Reply(status, body, headers));
    }
}
