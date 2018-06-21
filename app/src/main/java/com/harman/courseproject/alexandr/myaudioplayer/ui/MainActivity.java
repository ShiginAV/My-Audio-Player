package com.harman.courseproject.alexandr.myaudioplayer.ui;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import com.harman.courseproject.alexandr.myaudioplayer.R;
import com.harman.courseproject.alexandr.myaudioplayer.download.DownloadActivity;
import com.harman.courseproject.alexandr.myaudioplayer.service.StorageUtil;
import com.harman.courseproject.alexandr.myaudioplayer.service.MediaPlayerService;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ArrayList<Audio> audioList;
    private MediaControllerCompat mediaController;
    private MediaPlayerService.PlayerServiceBinder mediaPlayerServiceBinder;
    private ServiceConnection serviceConnection;

    private ImageView collapsingImageView;
    private int drawable_albumArt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final int drawable_play = android.R.drawable.ic_media_play;
        final int drawable_pause = android.R.drawable.ic_media_pause;
        drawable_albumArt = R.drawable.image5;
        collapsingImageView = findViewById(R.id.collapsingImageView);

        loadAudio();
        initRecyclerView();
        /*
        Floating menu
        */
        final com.getbase.floatingactionbutton.FloatingActionButton fabDownload = findViewById(R.id.fab_download);
        fabDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DownloadActivity.class);
                startActivity(intent);
            }
        });

        final com.getbase.floatingactionbutton.FloatingActionButton fabPlayPause = findViewById(R.id.fab_play_pause);
        fabPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController.getPlaybackState() != null) {
                    if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
                        fabPlayPause.setIcon(drawable_pause);
                        mediaController.getTransportControls().pause();
                    } else {
                        fabPlayPause.setIcon(drawable_play);
                        mediaController.getTransportControls().play();
                    }
                }
            }
        });

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mediaPlayerServiceBinder = (MediaPlayerService.PlayerServiceBinder) service;
                try {
                    mediaController = new MediaControllerCompat(MainActivity.this, mediaPlayerServiceBinder.getMediaSessionToken());
                }
                catch (RemoteException e) {
                    mediaController = null;
                }
                //Toast.makeText(MainActivity.this, "Service bound", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mediaPlayerServiceBinder = null;
                if (mediaController != null) {
                    mediaController = null;
                }
            }
        };
        bindService(new Intent(this, MediaPlayerService.class), serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaPlayerServiceBinder = null;
        if (mediaController != null) {
            mediaController = null;
        }
        unbindService(serviceConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initRecyclerView() {
        if (audioList != null && audioList.size() > 0) {
            RecyclerView recyclerView = findViewById(R.id.recyclerview);
            RecyclerViewAdapter adapter = new RecyclerViewAdapter(audioList, getApplication());
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.addOnItemTouchListener(new TouchListener(this, new TouchListener.OnItemMotionEventListener() {
                @Override
                public void onItemClick(View view, int index) {
                    playAudio(index);
                    showAlbumArt();
                }
            }));
        }
    }

    private void showAlbumArt() {
        if (mediaController.getMetadata() != null) {
            Bitmap albumArt = mediaController.getMetadata().getDescription().getIconBitmap();
            if (albumArt != null) {
                collapsingImageView.setImageBitmap(albumArt);
            } else {
                collapsingImageView.setImageResource(drawable_albumArt);
            }
        }
    }

    private void playAudio(int audioIndex) {
        StorageUtil storage = new StorageUtil(getApplicationContext());
        storage.storeAudio(audioList);
        storage.storeAudioIndex(audioIndex);

        if (mediaController != null) {
            mediaController.getTransportControls().play();
        }
    }

    private void loadAudio() {
        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
        Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);
        Cursor cursorAlbum;

        if (cursor != null && cursor.getCount() > 0) {
            audioList = new ArrayList<>();
            while (cursor.moveToNext()) {
                String albumArt = null;
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));

                Long albumId = Long.valueOf(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)));
                cursorAlbum = contentResolver.query((MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI)
                        , new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART}
                        , MediaStore.Audio.Albums._ID + "=" + albumId, null, null);
                if(cursorAlbum != null && cursorAlbum.moveToFirst()) {
                    albumArt = cursorAlbum.getString(cursorAlbum.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
                }
                if (cursorAlbum != null) {
                    cursorAlbum.close();
                }
                audioList.add(new Audio(data, title, album, artist, albumArt));
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }
}
