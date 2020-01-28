package com.guichaguri.trackplayer.service.Tasks.DownloadTasks;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.util.Util;
import com.guichaguri.trackplayer.module.MusicEvents;
import com.guichaguri.trackplayer.service.MusicService;
import com.guichaguri.trackplayer.service.Utils;

import java.io.File;
import java.io.FileOutputStream;


public class DownloadTask extends AsyncTask<TaskParams, Integer, String> {

    private MusicService service;
    private String key;
    private Uri uri;
    private int length;
    private int progress;
    private Promise callback;
    private String path;
    private long time;
    private int totalProgress;

    @Override
    protected void onPreExecute() {
        progress = 0;
        time = System.currentTimeMillis();
        totalProgress = 0;
    }

    @Override
    protected String doInBackground(TaskParams... params) {
        callback = params[0].callback;
        service = params[0].service;
        Context ctx = params[0].ctx;
        Cache cache = params[0].cache;
        key = params[0].key;
        uri = params[0].uri;
        length = params[0].length;
        path = params[0].path;

        boolean ForceOverWrite = params[0].ForceOverWrite;
        String userAgent = Util.getUserAgent(ctx, "react-native-track-player");
        Log.d(Utils.LOG, "cache download : getUserAgent: " + userAgent + "//");
        byte[] buffer = new byte[102400];


        DefaultHttpDataSource ds = new DefaultHttpDataSource(
                userAgent
        );
        CacheDataSource dataSource = new CacheDataSource(cache, ds);
        try {
            dataSource.open(new DataSpec(uri, 0, length, key));
            File file = new File(path);
            if (file.exists() && ForceOverWrite) {
                file.delete();
            } else if (file.exists() && !ForceOverWrite) {
                throw new Exception("File exists");
            }

            FileOutputStream fs = new FileOutputStream(path);
            int read = 0;
            while ((read = dataSource.read(buffer, 0, buffer.length)) > 0) {
                if (isCancelled()) break;
                fs.write(buffer, 0, read);
                totalProgress = totalProgress + read;
                publishProgress(totalProgress);
            }
            fs.close();
            dataSource.close();
            ds.close();
            return (file.getAbsolutePath());


        } catch (Exception e) {
            if (isCancelled()) {
                Log.d(Utils.LOG, "Download: BackGroundTask Interrupted as expected//");
                return "0";
            } else {
                e.printStackTrace();
                callback.reject(e);
            }

        }


        return "0";
    }

    @Override
    protected void onProgressUpdate(Integer... prog) {
        if (System.currentTimeMillis() - time > 1000) {
            time = System.currentTimeMillis();
            Bundle bundle = new Bundle();
            bundle.putString("key", key);
            bundle.putInt("progress", prog[0]);
            bundle.putInt("length", length);
            bundle.putString("url", uri.toString());
            service.emit(MusicEvents.DOWNLOAD_PROGRESS, bundle);
        }
    }

    @Override
    protected void onPostExecute(String path) {
        if (isCancelled()) {
            try {
                File partiallyDownloadedFile = new File(path);
                if (partiallyDownloadedFile.exists()) partiallyDownloadedFile.delete();
                Bundle bundle = new Bundle();
                bundle.putString("key", key);
                bundle.putString("status", "resolved");
                service.emit(MusicEvents.DOWNLOAD_CANCELLED, bundle);
            } catch (Exception e) {
                Bundle bundle = new Bundle();
                bundle.putString("key", key);
                bundle.putString("status", "rejected");
                bundle.putString("error", e.toString());
                service.emit(MusicEvents.DOWNLOAD_CANCELLED, bundle);
                e.printStackTrace();
            }
        } else if (path != "0") {
            Bundle bundle = new Bundle();
            bundle.putString("key", key);
            bundle.putInt("length", length);
            bundle.putString("url", uri.toString());
            bundle.putString("path", path);
            service.emit(MusicEvents.DOWNLOAD_COMPLETED, bundle);
            callback.resolve(path);
        }

    }

    @Override
    protected void onCancelled() {
        Log.d(Utils.LOG, "Download: BackGroundTask Interrupted as expected//");
        try {
            File partiallyDownloadedFile = new File(path);
            if (partiallyDownloadedFile.exists()) partiallyDownloadedFile.delete();
            Bundle bundle = new Bundle();
            bundle.putString("key", key);
            bundle.putString("status", "resolved");
            service.emit(MusicEvents.DOWNLOAD_CANCELLED, bundle);
        } catch (Exception e) {
            Bundle bundle = new Bundle();
            bundle.putString("key", key);
            bundle.putString("status", "rejected");
            bundle.putString("error", e.toString());
            service.emit(MusicEvents.DOWNLOAD_CANCELLED, bundle);
            e.printStackTrace();
        }

    }
}
