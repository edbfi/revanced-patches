// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import android.util.Log;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Answers the app's Imgur requests from Imgur's own API. The app loads albums and single images through its
 * developer's mirror, {@code images.syncforreddit.com}, which no longer exists, and uploads through a RapidAPI proxy
 * that refuses its key (401). Its parsers already read the API's shapes.
 */
public final class Imgur {
    private static final String MIRROR = "https://images.syncforreddit.com/";
    private static final String ALBUM_MIRROR = MIRROR + "a/";
    private static final String IMAGE_MIRROR = MIRROR + "image/";
    private static final String UPLOAD = "https://imgur-apiv3.p.rapidapi.com/3/image";
    private static final String API = "https://api.imgur.com/3/";
    // An upload sends the whole image, base64-encoded.
    private static final int UPLOAD_TIMEOUT_MS = 60_000;

    /** Imgur's API, sent the client ID and the system's own User-Agent; a fake in tests. */
    interface Api {
        Http.Reply send(String method, String path, byte[] body, String contentType) throws Exception;
    }

    private static final Api IMGUR = (method, path, body, contentType) -> {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Client-ID " + clientId());
        headers.put("User-Agent", Http.systemUserAgent());
        if (contentType != null) headers.put("Content-Type", contentType);
        return Http.send(method, API + path, headers, body, true, body == null ? Http.TIMEOUT_MS : UPLOAD_TIMEOUT_MS);
    };

    private Imgur() {}

    /** Turned on by the "Fix Imgur" patch. */
    public static boolean isEnabled() {
        return false;
    }

    /** The patch's "Imgur client ID" option. */
    public static String clientId() {
        return "";
    }

    static boolean handles(String url) {
        return url != null && (url.startsWith(ALBUM_MIRROR) || url.startsWith(IMAGE_MIRROR) || url.startsWith(UPLOAD));
    }

    /**
     * Never returns null: the app's own requests go to hosts that no longer answer, and would send its old Imgur keys.
     * A failure becomes an error response, which the app reports as a failed load or upload.
     */
    static NetworkResponse answer(Request<?> request) {
        String url = request.getUrl();
        try {
            boolean upload = url.startsWith(UPLOAD);
            byte[] body = upload ? request.getBody() : null;
            String contentType = upload ? request.getBodyContentType() : null;
            return VolleyHook.response(reply(url, body, contentType, IMGUR));
        } catch (Exception error) {
            Log.w(VolleyHook.TAG, "Imgur request failed: " + url, error);
            return VolleyHook.json(502, new byte[0]);
        }
    }

    /** Imgur's answer to the app's request: an upload, an image, or an album. */
    static Http.Reply reply(String url, byte[] body, String contentType, Api api) throws Exception {
        if (url.startsWith(UPLOAD)) {
            if (body == null) throw new IllegalArgumentException("Upload without a body");
            return api.send("POST", "image", body, contentType);
        }
        String id = mirrorId(url);
        if (id == null) throw new IllegalArgumentException("Unexpected Imgur URL: " + url);
        if (url.startsWith(IMAGE_MIRROR)) return api.send("GET", "image/" + id, null, null);
        Http.Reply album = api.send("GET", "album/" + id, null, null);
        if (album.status != 404) return album;
        // Links to a single image posted to Imgur's gallery look like album links.
        Http.Reply image = api.send("GET", "image/" + id, null, null);
        if (image.status != 200) return album;
        return new Http.Reply(200, albumOf(image.body), VolleyHook.jsonHeaders());
    }

    /** The ID at the end of the app's mirror URL, {@code …/a/<hash>/<id>}, or null. Imgur IDs are case-sensitive. */
    static String mirrorId(String url) {
        if (url == null || !url.startsWith(MIRROR)) return null;
        String id = url.substring(url.lastIndexOf('/') + 1);
        return id.matches("[A-Za-z0-9]+") ? id : null;
    }

    /** An image response, {@code {"data":{image}}}, as an album response holding only that image. */
    static byte[] albumOf(byte[] imageResponse) throws JSONException {
        JSONObject image = new JSONObject(new String(imageResponse, StandardCharsets.UTF_8)).getJSONObject("data");
        JSONObject album = new JSONObject().put("images", new JSONArray().put(image));
        return new JSONObject().put("data", album).toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Called by the app's Imgur ID parser with one path segment, before it cuts the segment at the first dash. Imgur's
     * newer links put a title before the ID ({@code /gallery/funny-cat-AbC1234}): those become the ID with whatever
     * followed it; every other segment is returned as it was.
     */
    public static String withoutTitle(String segment) {
        if (segment == null) return null;
        int end = segment.length();
        for (char stop : new char[] {'#', '?', '&', '.'}) {
            int at = segment.indexOf(stop);
            if (at >= 0 && at < end) end = at;
        }
        String name = segment.substring(0, end);
        int dash = name.lastIndexOf('-');
        if (dash <= 0) return segment;
        String id = name.substring(dash + 1);
        // The IDs Imgur gives new posts have 7 characters.
        return id.matches("[A-Za-z0-9]{7}") ? id + segment.substring(end) : segment;
    }
}
