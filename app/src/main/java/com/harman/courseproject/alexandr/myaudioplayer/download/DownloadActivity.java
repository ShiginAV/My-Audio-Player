package com.harman.courseproject.alexandr.myaudioplayer.download;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;

import com.harman.courseproject.alexandr.myaudioplayer.R;

public class DownloadActivity extends AppCompatActivity {
    private Button downloadButton;
    private static String youtubeLink;
    private DownloadManager manager;
    private EditText url;
    private Button cancelDownloadButton;
    private long downloadId;
    private SharedPreferences sharedPreferences;
    private final String AUDIO_INDEX = "audioIndex";
    private final String DOWNLOAD_ID = "com.harman.courseproject.alexandr.myaudioplayer.download.DOWNLOAD";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        url = findViewById(R.id.url_text);
        downloadButton = findViewById(R.id.button_download);
        cancelDownloadButton = findViewById(R.id.cancel);
        sharedPreferences = getSharedPreferences(DOWNLOAD_ID, Context.MODE_PRIVATE);
        manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        if (savedInstanceState == null && Intent.ACTION_SEND.equals(getIntent().getAction())
                && getIntent().getType() != null && "text/plain".equals(getIntent().getType())) {

            url.setText(getIntent().getStringExtra(Intent.EXTRA_TEXT));
        }

        // Download audio file
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ytLink = url.getText().toString();

                if ((ytLink.contains("://youtu.be/") || ytLink.contains("youtube.com/watch?v="))) {
                    youtubeLink = ytLink;
                    getYoutubeDownloadUrl(youtubeLink);
                } else {
                    Toast.makeText(DownloadActivity.this, R.string.error_no_yt_link, Toast.LENGTH_SHORT).show();
                }
            }
        });
        // Cancel downloading
        cancelDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sharedPreferences.contains(AUDIO_INDEX)) {
                    downloadId = sharedPreferences.getLong(AUDIO_INDEX, -1);
                    manager.remove(downloadId);
                }
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    private void getYoutubeDownloadUrl(String youtubeLink) {
        new YouTubeExtractor(this) {
            @Override
            protected void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                YtFile audioFile = null;
                for (int i = 0; i < ytFiles.size(); i++) {
                    YtFile ytFile = ytFiles.get(ytFiles.keyAt(i));
                    if (ytFile.getFormat().getHeight() == -1) {
                        audioFile = ytFile;
                        break;
                    }
                }
                String videoTitle = vMeta.getTitle();
                String filename;
                if (videoTitle.length() > 55) {
                    filename = videoTitle.substring(0, 55);
                } else {
                    filename = videoTitle;
                }
                filename = filename.replaceAll("\\\\|>|<|\"|\\||\\*|\\?|%|:|#|/", "");
                if (audioFile != null) {
                    downloadId = downloadFromUrl(audioFile.getUrl(), videoTitle, filename + "." + audioFile.getFormat().getExt());
                    cacheDownloadIds(String.valueOf(downloadId));

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putLong(AUDIO_INDEX, downloadId);
                    editor.apply();
                }
            }
        }.extract(youtubeLink, true, false);
    }

    private long downloadFromUrl(String youtubeDlUrl, String downloadTitle, String fileName) {
        Uri uri = Uri.parse(youtubeDlUrl);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle(downloadTitle);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        return manager.enqueue(request);
    }

    private void cacheDownloadIds(String downloadIds) {
        File dlCacheFile = new File(this.getCacheDir().getAbsolutePath() + "/" + downloadIds);
        try {
            dlCacheFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
