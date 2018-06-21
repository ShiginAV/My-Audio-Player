package com.harman.courseproject.alexandr.myaudioplayer.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.harman.courseproject.alexandr.myaudioplayer.R;
import com.harman.courseproject.alexandr.myaudioplayer.ui.Audio;
import com.harman.courseproject.alexandr.myaudioplayer.ui.MainActivity;

import java.io.IOException;
import java.util.ArrayList;

public class MediaPlayerService extends Service {
    private final String NOTIFICATION_DEFAULT_CHANNEL_ID = "default_channel";
    private final int NOTIFICATION_ID = 404;

    private final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

    private final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(
                      PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    );

    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private AudioManager audioManager;

    private MediaSessionCompat mediaSession;

    private MediaPlayer mediaPlayer;
    ArrayList<Audio> audioList;
    private int resumePosition;

    private int audioIndex = -1;
    private Audio activeAudio;



    @Override
    public void onCreate() {
        super.onCreate();

        callStateListener();

        registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }

        initMediaSession();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaSession.release();

        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }

        audioManager.abandonAudioFocus(audioFocusChangeListener);

        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        unregisterReceiver(becomingNoisyReceiver);
        new StorageUtil(getApplicationContext()).clearCachedAudioPlayList();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new PlayerServiceBinder();
    }

    public class PlayerServiceBinder extends Binder {
        public MediaSessionCompat.Token getMediaSessionToken() {
            return mediaSession.getSessionToken();
        }
    }

    private void initMediaPlayer() {
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                playMedia();
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stopMedia();
                stopSelf();
            }
        });
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(activeAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }
    /*
    Basic actions for playing media
    */
    private String dataOfPlayingAudio = null;
    private void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            if (dataOfPlayingAudio != null && dataOfPlayingAudio.equals(activeAudio.getData())) {
                mediaPlayer.seekTo(resumePosition);
            } else {
                resumePosition = 0;
            }
            mediaPlayer.start();
        }
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();

            dataOfPlayingAudio = activeAudio.getData();
        }
    }

    void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
        }
    }

    private void skipToNextMedia() {
        if (audioIndex == audioList.size() - 1) {
            audioIndex = 0;
            activeAudio = audioList.get(audioIndex);
        } else {
            activeAudio = audioList.get(++audioIndex);
        }
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToPreviousMedia() {
        if (audioIndex == 0) {
            audioIndex = audioList.size() - 1;
            activeAudio = audioList.get(audioIndex);
        } else {
            activeAudio = audioList.get(--audioIndex);
        }
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "MediaPlayerService");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(mediaSessionCallback);

        Context appContext = getApplicationContext();
        Intent activityIntent = new Intent(appContext, MainActivity.class);
        mediaSession.setSessionActivity(PendingIntent.getActivity(appContext, 0, activityIntent, 0));

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, appContext, MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(appContext, 0, mediaButtonIntent, 0));
    }
    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        int currentState = PlaybackStateCompat.STATE_STOPPED;
        @Override
        public void onPlay() {
            super.onPlay();
            startService(new Intent(getApplicationContext(), MediaPlayerService.class));
            // Get audio from storage
            try {
                StorageUtil storage = new StorageUtil(getApplicationContext());
                audioList = storage.loadAudio();
                audioIndex = storage.loadAudioIndex();
            } catch (NullPointerException e) {
                stopSelf();
            }
            // Set current audio
            if (audioIndex != -1 && audioIndex < audioList.size()) {
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }
            updateMetaData();
            int audioFocusResult = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return;
            }
            mediaSession.setActive(true);

            initMediaPlayer();

            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_PLAYING;
            refreshNotificationAndForegroundStatus(currentState);
        }

        @Override
        public void onPause() {
            super.onPause();
            pauseMedia();
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());

            currentState = PlaybackStateCompat.STATE_PAUSED;
            refreshNotificationAndForegroundStatus(currentState);
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            skipToNextMedia();

            updateMetaData();
            refreshNotificationAndForegroundStatus(currentState);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            skipToPreviousMedia();

            updateMetaData();
            refreshNotificationAndForegroundStatus(currentState);
        }

        @Override
        public void onStop() {
            super.onStop();
            stopMedia();

            mediaSession.setActive(false);
            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());

            currentState = PlaybackStateCompat.STATE_STOPPED;
            refreshNotificationAndForegroundStatus(currentState);

            stopSelf();
        }
        private void updateMetaData() {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeFile(activeAudio.getAlbumArt()));
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle());
            mediaSession.setMetadata(metadataBuilder.build());
        }
    };

    /*
    AudioFocus
    */
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (!mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.setVolume(1.0f, 1.0f);
                    }
                    break;
            }
        }
    };

    /*
    (BroadcastReceiver) ACTION_AUDIO_BECOMING_NOISY
    */
    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pauseMedia();
            }
        }
    };

    private void refreshNotificationAndForegroundStatus(int playbackState) {
        switch (playbackState) {
            case PlaybackStateCompat.STATE_PLAYING: {
                startForeground(NOTIFICATION_ID, getNotification(playbackState));
                break;
            }
            case PlaybackStateCompat.STATE_PAUSED: {
                NotificationManagerCompat.from(MediaPlayerService.this).notify(NOTIFICATION_ID, getNotification(playbackState));
                stopForeground(false);
                break;
            }
            default: {
                stopForeground(true);
                break;
            }
        }
    }

    private Notification getNotification(int playbackState) {
        NotificationCompat.Builder builder = MediaStyleHelper.from(this, mediaSession);
        // add buttons previous
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, "previous"
                , MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));

        if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            // add buttons pause
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, "pause"
                    , MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        } else {
            // add buttons play
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, "play"
                    , MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        }
        // add buttons next
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, "next"
                , MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));

        builder.setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)));
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        builder.setShowWhen(false);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setOnlyAlertOnce(true);
        builder.setChannelId(NOTIFICATION_DEFAULT_CHANNEL_ID);

        return builder.build();
    }

    /*
    Call state
    */
    private void callStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }
}
