package com.example.nhac;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {

    public static final String CHANNEL_ID = "music_channel";
    private static final int NOTIFICATION_ID = 1;

    // Action điều khiển từ thông báo
    public static final String ACTION_PLAY_PAUSE = "com.example.nhac.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.example.nhac.ACTION_NEXT";
    public static final String ACTION_PREV = "com.example.nhac.ACTION_PREV";
    public static final String ACTION_STOP = "com.example.nhac.ACTION_STOP";

    private final IBinder binder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private final List<Song> songList = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isForeground = false;

    public interface OnMusicChangeListener {
        void onMusicChanged();
    }

    private OnMusicChangeListener listener;

    public void setOnMusicChangeListener(OnMusicChangeListener listener) {
        this.listener = listener;
    }

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Thêm vài bài nhạc mẫu từ res/raw
        songList.add(new Song("Giai điệu vui tươi", "Nhạc mẫu 1", R.raw.bai_1));
        songList.add(new Song("Giai điệu nhẹ nhàng", "Nhạc mẫu 2", R.raw.bai_2));
        songList.add(new Song("Giai điệu sôi động", "Nhạc mẫu 3", R.raw.bai_3));
    }

    // Xử lý khi bấm nút trên thông báo
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE:
                    playPause();
                    break;
                case ACTION_NEXT:
                    nextSong();
                    break;
                case ACTION_PREV:
                    prevSong();
                    break;
                case ACTION_STOP:
                    stopMusic();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Phát bài theo vị trí
    public void playSongAt(int index) {
        if (index < 0 || index >= songList.size()) return;
        currentIndex = index;

        releaseMediaPlayer();
        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(songList.get(currentIndex).getResId());
            if (afd == null) return;

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mediaPlayer.prepare();
            mediaPlayer.setVolume(1.0f, 1.0f);

            // Hết bài tự qua bài kế
            mediaPlayer.setOnCompletionListener(mp -> nextSong());
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        showNotification();
        notifyListener();
    }

    public void playPause() {
        if (mediaPlayer == null) {
            playSongAt(currentIndex);
            return;
        }

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.start();
        }
        showNotification();
        notifyListener();
    }

    public void nextSong() {
        int next = (currentIndex + 1) % songList.size();
        playSongAt(next);
    }

    public void prevSong() {
        int prev = (currentIndex - 1 + songList.size()) % songList.size();
        playSongAt(prev);
    }

    // Dừng nhạc và tắt Foreground Service
    public void stopMusic() {
        releaseMediaPlayer();
        isForeground = false;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
        notifyListener();
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public Song getCurrentSong() {
        return songList.get(currentIndex);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public List<Song> getSongList() {
        return songList;
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }

    public void seekTo(int pos) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(pos);
        }
    }

    // Hiện thông báo để giữ Service chạy ngầm
    private void showNotification() {
        Song song = getCurrentSong();
        String status = isPlaying() ? "Đang phát" : "Đã tạm dừng";

        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        PendingIntent prevIntent = createActionIntent(ACTION_PREV, 1);
        PendingIntent playPauseIntent = createActionIntent(ACTION_PLAY_PAUSE, 2);
        PendingIntent nextIntent = createActionIntent(ACTION_NEXT, 3);
        PendingIntent stopIntent = createActionIntent(ACTION_STOP, 4);

        int playPauseIcon = isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play;
        String playPauseText = isPlaying() ? "Tạm dừng" : "Phát";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(song.getTitle())
                .setContentText(song.getArtist() + " • " + status)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(contentIntent)
                .setOngoing(isPlaying())
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_prev, "Trước", prevIntent)
                .addAction(playPauseIcon, playPauseText, playPauseIntent)
                .addAction(R.drawable.ic_next, "Sau", nextIntent)
                .addAction(R.drawable.ic_stop, "Dừng", stopIntent)
                .build();

        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            isForeground = true;
        } else {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private PendingIntent createActionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Trình phát nhạc",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onMusicChanged();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
    }
}
