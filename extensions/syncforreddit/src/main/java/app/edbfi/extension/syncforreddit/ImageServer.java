// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;

/**
 * Runs in the app's image client, after the app's own interceptor. The app loads previews of some links in comments
 * and posts (Imgur albums, Tenor, Gfycat, website previews) from its developer's image server, which refuses links it
 * hasn't cached (401). Imgur album and Tenor previews come from Imgur and Tenor instead, and website previews from the
 * linked page itself; every other preview fails here, so the image client no longer sends the links people scroll past
 * to that server.
 */
public final class ImageServer implements Interceptor {
    static final String PREFIX = "https://ap.syncforreddit.com/image?url=";
    // Glide asks again each time a failed preview scrolls back into view.
    private static final long RETRY_AFTER_MS = 5 * 60_000;
    private static final int PAGE_TIMEOUT_MS = 5_000;
    private static final Map<String, Long> FAILED = new LinkedHashMap<String, Long>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 256;
        }
    };

    private ImageServer() {}

    /** Replaces the app's {@code build()} of its image client. */
    public static OkHttpClient build(OkHttpClient.Builder builder) {
        return builder.addInterceptor(new ImageServer()).addNetworkInterceptor(new Guard()).build();
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
            WebsitePreviews.Pages pages = WebsitePreviews.isEnabled() ? link -> fetchHead(chain, link) : null;
            String image = thumbnailUrl(url, Imgur.isEnabled() ? Imgur.IMGUR : null, Tenor.isEnabled() ? Tenor.TENOR : null, pages);
            // The app's interceptor set the User-Agent it sends Reddit; other sites get the system's own.
            thumbnail = request.newBuilder()
                    .url(image)
                    .header("User-Agent", Http.systemUserAgent())
                    .tag(Guard.class, Guard.ON)
                    .build();
        } catch (Exception error) {
            IOException failure = error instanceof IOException ? (IOException) error : new IOException("Preview failed: " + url, error);
            if (worthRemembering(failure)) failed(url);
            throw failure;
        }
        Response response;
        try {
            response = chain.proceed(thumbnail);
        } catch (IOException failure) {
            if (worthRemembering(failure)) failed(url);
            throw failure;
        }
        // Glide fails on any other answer: don't ask the page again each time the preview scrolls into view.
        if (response.code() / 100 != 2) failed(url);
        return response;
    }

    /**
     * Whether a failure is an answer worth remembering: Imgur, Tenor or a page had nothing to show, or refused to say
     * (a 429 or 5xx, which also spares the shared client's quota). A lost connection is tried again when the preview
     * next shows, and a preview nothing handles costs nothing to fail again.
     */
    static boolean worthRemembering(IOException failure) {
        for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (!(cause instanceof IOException)) return true;
        }
        return false;
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
     * the image a website names for other https links, nowhere for the rest. A null API is a fix that is off.
     */
    static String thumbnailUrl(String url, Imgur.Api imgur, Tenor.Api tenor, WebsitePreviews.Pages pages) throws IOException {
        String link = url.substring(PREFIX.length());
        String thumbnail = null;
        try {
            if (imgur != null) thumbnail = Imgur.albumPreview(url, imgur);
            if (thumbnail == null && tenor != null && Tenor.handles(link)) thumbnail = Tenor.thumbnail(link, tenor);
            if (thumbnail == null && pages != null && !Tenor.handles(link) && WebsitePreviews.handles(link)) {
                thumbnail = WebsitePreviews.image(link, pages);
            }
        } catch (Exception error) {
            throw new IOException("Preview failed: " + url, error);
        }
        if (thumbnail == null) throw new IOException("Sync's image server is gone: " + url);
        return thumbnail;
    }

    /**
     * The head of the page at the link, fetched through the rest of the image client so {@link Guard} checks every hop:
     * a fresh request, so the page gets no header the app sends Reddit, and the client keeps no cookies.
     */
    private static WebsitePreviews.Page fetchHead(Chain chain, String link) throws IOException {
        Request page;
        try {
            page = new Request.Builder()
                    .url(link)
                    .header("User-Agent", Http.systemUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml")
                    .tag(Guard.class, Guard.ON)
                    .build();
        } catch (IllegalArgumentException error) {
            throw new IOException("Not a URL: " + link, error);
        }
        long deadline = System.currentTimeMillis() + WebsitePreviews.PAGE_DEADLINE_MS;
        Chain bounded = chain.withConnectTimeout(PAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .withReadTimeout(PAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try (Response response = bounded.proceed(page)) {
            if (response.code() != 200) {
                throw new IOException("Preview failed", new IllegalStateException("The page answered " + response.code() + ": " + link));
            }
            String contentType = response.header("Content-Type");
            ResponseBody body = response.body();
            byte[] head = new byte[0];
            if (WebsitePreviews.isHtml(contentType) && body != null) {
                try (InputStream in = body.byteStream()) {
                    head = WebsitePreviews.readHead(in, WebsitePreviews.MAX_HEAD_BYTES, deadline);
                }
            }
            return new WebsitePreviews.Page(response.request().url().toString(), contentType, head);
        }
    }

    /**
     * A network interceptor: checks each hop of a website preview, redirects included, once connected and before
     * anything is sent. It must be https, and reach a public address. Behind a proxy the address is the proxy's, and
     * only the scheme is checked.
     */
    static final class Guard implements Interceptor {
        static final Guard ON = new Guard();

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (request.tag(Guard.class) == null) return chain.proceed(request);
            if (!request.isHttps()) throw refused("Not https: " + request.url());
            Route route = chain.connection() == null ? null : chain.connection().route();
            if (route == null) throw refused("No route to check: " + request.url());
            if (route.proxy().type() == Proxy.Type.DIRECT) {
                InetSocketAddress address = route.socketAddress();
                if (!WebsitePreviews.isPublic(address.getAddress())) {
                    throw refused("Not a public address (" + address.getAddress() + "): " + request.url());
                }
            }
            return chain.proceed(request);
        }

        private static IOException refused(String why) {
            return new IOException("Preview refused", new IllegalStateException(why));
        }
    }
}
