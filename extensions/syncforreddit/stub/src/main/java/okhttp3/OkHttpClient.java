// SPDX-License-Identifier: AGPL-3.0-only
package okhttp3;

/** Compile-only stand-in for the OkHttp client bundled in the app. */
public class OkHttpClient {
    public static class Builder {
        public final Builder addInterceptor(Interceptor interceptor) {
            throw new UnsupportedOperationException("stub");
        }

        public final OkHttpClient build() {
            throw new UnsupportedOperationException("stub");
        }
    }
}
