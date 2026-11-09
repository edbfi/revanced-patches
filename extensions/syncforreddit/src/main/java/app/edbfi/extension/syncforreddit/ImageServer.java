// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Runs in the app's image client, after the app's own interceptor. The app loads previews of some links in comments
 * and posts (Imgur albums, Tenor, Gfycat, website previews) from its developer's image server, which refuses every
 * request (401). Imgur album previews come from Imgur instead; every other preview fails here, so the image client no
 * longer sends the links people scroll past to that server.
 */
public final class ImageServer implements Interceptor {
    static final String PREFIX = "https://ap.syncforreddit.com/image?url=";

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
        Request thumbnail;
        try {
            // The app's interceptor set the User-Agent it sends Reddit; Imgur gets the system's own.
            thumbnail = request.newBuilder()
                    .url(thumbnailUrl(url, Imgur.isEnabled(), Imgur.IMGUR))
                    .header("User-Agent", Http.systemUserAgent())
                    .build();
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Imgur album preview failed: " + url, error);
        }
        return chain.proceed(thumbnail);
    }

    /** Where to load a preview from the developer's image server instead: Imgur for an album, nowhere for the rest. */
    static String thumbnailUrl(String url, boolean imgur, Imgur.Api api) throws IOException {
        String thumbnail;
        try {
            thumbnail = imgur ? Imgur.albumPreview(url, api) : null;
        } catch (Exception error) {
            throw new IOException("Imgur album preview failed: " + url, error);
        }
        if (thumbnail == null) throw new IOException("Sync's image server is gone: " + url);
        return thumbnail;
    }
}
