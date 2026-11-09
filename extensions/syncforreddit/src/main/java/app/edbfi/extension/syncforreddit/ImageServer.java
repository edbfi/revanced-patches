// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Dns;
import okhttp3.HttpUrl;
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

    // The image client's DNS, so a host is checked with the addresses OkHttp will connect to.
    private static volatile Dns dns = host -> Arrays.asList(InetAddress.getAllByName(host));
    // Cancels a page fetch that runs past its deadline, however slowly the site sends.
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "WebsitePreviews watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private ImageServer() {}

    /** Replaces the app's {@code build()} of its image client. */
    public static OkHttpClient build(OkHttpClient.Builder builder) {
        OkHttpClient client = builder.addInterceptor(new ImageServer()).addNetworkInterceptor(new Guard()).build();
        dns = client.dns();
        return client;
    }

    /** Throws only IOException: anything else thrown here would crash the app on OkHttp's thread. */
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        if (!url.startsWith(PREFIX)) return chain.proceed(request);
        if (failedRecently(url)) throw new IOException("Preview failed recently: " + url);
        try {
            WebsitePreviews.Pages pages = WebsitePreviews.isEnabled() ? link -> fetchHead(chain, link) : null;
            String image = thumbnailUrl(url, Imgur.isEnabled() ? Imgur.IMGUR : null, Tenor.isEnabled() ? Tenor.TENOR : null, pages);
            // The app's interceptor set the User-Agent it sends Reddit; other sites get the system's own.
            Request.Builder builder = request.newBuilder().url(image).header("User-Agent", Http.systemUserAgent());
            boolean website = pages != null && WebsitePreviews.isWebsite(url.substring(PREFIX.length()));
            // A website names any image: Guard checks it as it checked the page. Imgur and Tenor are known hosts.
            if (website) builder.tag(Guard.class, Guard.ON);
            Request thumbnail = builder.build();
            if (website) WebsitePreviews.checkHost(thumbnail.url().host(), ImageServer::lookup);
            Response response = chain.proceed(thumbnail);
            // Glide fails on any other answer: don't ask the page again each time the preview scrolls into view.
            if (response.code() / 100 != 2) failed(url);
            return response;
        } catch (Exception error) {
            // OkHttp itself can throw others, such as a NumberFormatException for a huge Retry-After.
            IOException failure = error instanceof IOException ? (IOException) error : new IOException("Preview failed: " + url, error);
            if (worthRemembering(failure)) failed(url);
            throw failure;
        }
    }

    /**
     * Whether a failure is an answer worth remembering: Imgur, Tenor or a page had nothing to show, or refused to say
     * (a 429 or 5xx, which also spares the shared client's quota), or a page was refused. A lost connection is tried
     * again when the preview next shows, and a preview nothing handles costs nothing to fail again.
     */
    static boolean worthRemembering(IOException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof WebsitePreviews.Refused) return true;
        }
        // Not deeper: on Android a lost connection's IOException holds a platform exception (GaiException, ErrnoException).
        Throwable cause = failure.getCause();
        return cause != null && !(cause instanceof IOException);
    }

    private static List<InetAddress> lookup(String host) throws IOException {
        return dns.lookup(host);
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
            if (thumbnail == null && pages != null && WebsitePreviews.isWebsite(link)) {
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
        WebsitePreviews.checkHost(page.url().host(), ImageServer::lookup);
        long deadline = System.currentTimeMillis() + WebsitePreviews.PAGE_DEADLINE_MS;
        AtomicBoolean late = new AtomicBoolean();
        // Cancels the whole call, the preview's: it fails, as a page too slow to answer.
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
            late.set(true);
            chain.call().cancel();
        }, WebsitePreviews.PAGE_DEADLINE_MS, TimeUnit.MILLISECONDS);
        Chain bounded = chain.withConnectTimeout(PAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .withReadTimeout(PAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try (Response response = bounded.proceed(page)) {
            if (response.code() != 200) throw new WebsitePreviews.Refused("The page answered " + response.code() + ": " + link);
            String contentType = response.header("Content-Type");
            ResponseBody body = response.body();
            byte[] head = new byte[0];
            if (WebsitePreviews.isHtml(contentType) && body != null) {
                try (InputStream in = body.byteStream()) {
                    head = WebsitePreviews.readHead(in, WebsitePreviews.MAX_HEAD_BYTES, deadline);
                }
            }
            return new WebsitePreviews.Page(response.request().url().toString(), contentType, head);
        } catch (IOException failure) {
            if (late.get()) throw new WebsitePreviews.Refused("The page took too long to load: " + link);
            throw failure;
        } finally {
            watchdog.cancel(false);
            if (late.get()) throw new WebsitePreviews.Refused("The page took too long to load: " + link);
        }
    }

    /**
     * A network interceptor: checks each hop of a website preview once connected, before its request is sent. It must
     * be https and reach a public address, and a redirect may only lead to a public host, checked before OkHttp
     * follows it. Behind a proxy the address is the proxy's, so nothing is sent: the proxy would reach any host.
     */
    static final class Guard implements Interceptor {
        static final Guard ON = new Guard();

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (request.tag(Guard.class) == null) return chain.proceed(request);
            if (!request.isHttps()) throw new WebsitePreviews.Refused("Not https: " + request.url());
            Route route = chain.connection() == null ? null : chain.connection().route();
            if (route == null || route.proxy().type() != Proxy.Type.DIRECT) {
                throw new WebsitePreviews.Refused("Behind a proxy: " + request.url());
            }
            InetSocketAddress address = route.socketAddress();
            if (!WebsitePreviews.isPublic(address.getAddress())) {
                throw new WebsitePreviews.Refused("Not a public address (" + address.getAddress() + "): " + request.url());
            }
            Response response = chain.proceed(request);
            String location = response.code() / 100 == 3 ? response.header("Location") : null;
            if (location != null) {
                HttpUrl next = request.url().resolve(location);
                try {
                    if (next == null) throw new WebsitePreviews.Refused("Not a URL: " + location);
                    if (!next.toString().startsWith("https://")) throw new WebsitePreviews.Refused("Not https: " + next);
                    WebsitePreviews.checkHost(next.host(), ImageServer::lookup);
                } catch (IOException | RuntimeException refused) {
                    response.close();
                    throw refused instanceof IOException ? (IOException) refused : new IOException("Redirect failed", refused);
                }
            }
            return response;
        }
    }
}
