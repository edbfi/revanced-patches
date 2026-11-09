// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Runs in the app's image client, after the app's own interceptor. The app loads previews of some links in comments
 * and posts (Imgur albums, Tenor, Gfycat, website previews) from its developer's image server, which refuses links it
 * hasn't cached (401). Imgur album and Tenor previews come from Imgur and Tenor instead; every other preview fails
 * here, so the image client no longer sends the links people scroll past to that server.
 */
public final class ImageServer implements Interceptor {
    static final String PREFIX = "https://ap.syncforreddit.com/image?url=";
    // Glide asks again each time a failed preview scrolls back into view.
    private static final long RETRY_AFTER_MS = 5 * 60_000;
    private static final Map<String, Long> FAILED = new LinkedHashMap<String, Long>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 256;
        }
    };

    private ImageServer() {}

    /** Replaces the app's {@code build()} of its image client. */
    public static OkHttpClient build(OkHttpClient.Builder builder) {
        return builder.addInterceptor(new ImageServer()).build();
    }

    /** Throws only IOException: anything else thrown here would crash the app on OkHttp's thread. */
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        if (!url.startsWith(PREFIX)) return chain.proceed(request);
        if (failedRecently(url)) throw new IOException("Preview failed recently: " + url);
        Request thumbnail;
        try {
            // The app's interceptor set the User-Agent it sends Reddit; Imgur and Tenor get the system's own.
            thumbnail = request.newBuilder()
                    .url(thumbnailUrl(url, Imgur.isEnabled() ? Imgur.IMGUR : null, Tenor.isEnabled() ? Tenor.TENOR : null))
                    .header("User-Agent", Http.systemUserAgent())
                    .build();
        } catch (Exception error) {
            IOException failure = error instanceof IOException ? (IOException) error : new IOException("Preview failed: " + url, error);
            if (worthRemembering(failure)) failed(url);
            throw failure;
        }
        return chain.proceed(thumbnail);
    }

    /**
     * Whether a failure is an answer worth remembering: Imgur or Tenor had nothing to show, or refused to say (a 429 or
     * 5xx, which also spares the shared client's quota). A lost connection is tried again when the preview next shows,
     * and a preview nothing handles costs nothing to fail again.
     */
    static boolean worthRemembering(IOException failure) {
        Throwable cause = failure.getCause();
        return cause != null && !(cause instanceof IOException);
    }

    private static synchronized boolean failedRecently(String url) {
        Long at = FAILED.get(url);
        return at != null && System.currentTimeMillis() - at < RETRY_AFTER_MS;
    }

    private static synchronized void failed(String url) {
        FAILED.put(url, System.currentTimeMillis());
    }

    /**
     * Where to load a preview from the developer's image server instead: Imgur for an album, Tenor for a Tenor link,
     * nowhere for the rest. A null API is a fix that is off.
     */
    static String thumbnailUrl(String url, Imgur.Api imgur, Tenor.Api tenor) throws IOException {
        String link = url.substring(PREFIX.length());
        String thumbnail = null;
        try {
            if (imgur != null) thumbnail = Imgur.albumPreview(url, imgur);
            if (thumbnail == null && tenor != null && Tenor.handles(link)) thumbnail = Tenor.thumbnail(link, tenor);
        } catch (Exception error) {
            throw new IOException("Preview failed: " + url, error);
        }
        if (thumbnail == null) throw new IOException("Sync's image server is gone: " + url);
        return thumbnail;
    }
}
