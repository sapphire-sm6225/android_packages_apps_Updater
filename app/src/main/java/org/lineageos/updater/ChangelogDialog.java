/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Shows the build changelog inside the app instead of opening a browser.
 * Kept in its own file so the only upstream file that needs touching is the
 * menu handler in UpdatesActivity.
 */
final class ChangelogDialog {
    private static final String TAG = "ChangelogDialog";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BYTES = 512 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private ChangelogDialog() {
    }

    static void show(Activity activity, String url) {
        View view = LayoutInflater.from(activity).inflate(R.layout.changelog_dialog, null);
        View progress = view.findViewById(R.id.changelog_progress);
        View scroll = view.findViewById(R.id.changelog_scroll);
        TextView text = view.findViewById(R.id.changelog_text);
        View error = view.findViewById(R.id.changelog_error);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.changelog_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.changelog_open_in_browser,
                        (d, which) -> openInBrowser(activity, url))
                .show();

        Future<?> task = EXECUTOR.submit(() -> {
            String body = null;
            try {
                body = fetch(url);
            } catch (IOException e) {
                Log.e(TAG, "Could not load changelog from " + url, e);
            }
            final String result = body;
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed() || !dialog.isShowing()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                if (result == null || result.trim().isEmpty()) {
                    error.setVisibility(View.VISIBLE);
                } else {
                    text.setText(result);
                    scroll.setVisibility(View.VISIBLE);
                }
            });
        });
        dialog.setOnDismissListener(d -> task.cancel(true));
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server replied with " + code);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while (out.size() < MAX_BYTES && (read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void openInBrowser(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No application can open " + url, e);
        }
    }
}
