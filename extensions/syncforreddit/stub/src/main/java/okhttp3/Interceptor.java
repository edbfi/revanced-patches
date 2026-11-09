// SPDX-License-Identifier: AGPL-3.0-only
package okhttp3;

import java.io.IOException;

/** Compile-only stand-in for the OkHttp 5 interceptor bundled in the app. */
public interface Interceptor {
    Response intercept(Chain chain) throws IOException;

    interface Chain {
        Request request();

        Response proceed(Request request) throws IOException;
    }
}
