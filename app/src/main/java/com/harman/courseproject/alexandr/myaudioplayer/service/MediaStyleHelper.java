package com.harman.courseproject.alexandr.myaudioplayer.service;

import android.content.Context;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.app.NotificationCompat;

public class MediaStyleHelper {
    static NotificationCompat.Builder from(Context context, MediaSessionCompat mediaSession) {

        MediaControllerCompat controller = mediaSession.getController();
        MediaDescriptionCompat description = controller.getMetadata().getDescription();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "channel_id_01");
        builder
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription())
                .setLargeIcon(description.getIconBitmap())
                .setContentIntent(controller.getSessionActivity())
                .setDeleteIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        return builder;
    }
}
