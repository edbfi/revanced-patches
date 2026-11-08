// SPDX-License-Identifier: AGPL-3.0-only
package com.android.volley;

/** Compile-only stand-in for the Volley request class bundled in the app. */
public abstract class Request<T> {
    public String getUrl() {
        throw new UnsupportedOperationException("stub");
    }
}
