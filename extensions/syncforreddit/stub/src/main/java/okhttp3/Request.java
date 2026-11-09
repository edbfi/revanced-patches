// SPDX-License-Identifier: AGPL-3.0-only
package okhttp3;

/** Compile-only stand-in for the OkHttp request bundled in the app. */
public class Request {
    public final HttpUrl url() {
        throw new UnsupportedOperationException("stub");
    }

    public final Builder newBuilder() {
        throw new UnsupportedOperationException("stub");
    }

    public static class Builder {
        public Builder url(String url) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder header(String name, String value) {
            throw new UnsupportedOperationException("stub");
        }

        public Request build() {
            throw new UnsupportedOperationException("stub");
        }
    }
}
