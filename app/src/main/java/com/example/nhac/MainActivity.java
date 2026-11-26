package com.example.nhac;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvSongTitle, tvArtist, tvCurrentTime, tvTotalTime;
    private ImageButton btnPrev, btnPlayPause, btnNext, btnStop;
    private SeekBar seekBar;
    private ListView lvSongs;
    private ArrayAdapter<String> songAdapter;

    private MusicService musicService;
    private boolean isBound = false;

    // Cập nhật thanh tua nhạc mỗi 0.5s
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateSeekBarTask = new Runnable() {
        @Override
        public void run() {
            if (isBound && musicService != null && musicService.isPlaying()) {
                int pos = musicService.getCurrentPosition();
                int total = musicService.getDuration();
                seekBar.setMax(total);
                seekBar.setProgress(pos);
                tvCurrentTime.setText(formatTime(pos));
                tvTotalTime.setText(formatTime(total));
            }
            handler.postDelayed(this, 500);
        }
    };

    // Kết nối với MusicService
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;

            // Tự cập nhật UI khi đổi bài từ thông báo
            musicService.setOnMusicChangeListener(() -> runOnUiThread(() -> updateUI()));

            setupSongList();
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    systemBars.left + dpToPx(20),
                    systemBars.top + dpToPx(20),
                    systemBars.right + dpToPx(20),
                    systemBars.bottom + dpToPx(20)
            );
            return insets;
        });

        initViews();
        requestNotificationPermission();
        setupEvents();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        handler.post(updateSeekBarTask);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(updateSeekBarTask);
        // Unbind khi ẩn app, nhạc vẫn chạy ngầm nhờ Foreground Service
        if (isBound) {
            if (musicService != null) {
                musicService.setOnMusicChangeListener(null);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void initViews() {
        tvSongTitle = findViewById(R.id.tvSongTitle);
        tvArtist = findViewById(R.id.tvArtist);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnStop = findViewById(R.id.btnStop);
        seekBar = findViewById(R.id.seekBar);
        lvSongs = findViewById(R.id.lvSongs);
    }

    private void setupEvents() {
        btnPlayPause.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                startMusicService();
                musicService.playPause();
                updateUI();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                startMusicService();
                musicService.nextSong();
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                startMusicService();
                musicService.prevSong();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.stopMusic();
                seekBar.setProgress(0);
                tvCurrentTime.setText("00:00");
                updateUI();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && isBound && musicService != null) {
                    musicService.seekTo(progress);
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    // Đưa danh sách bài hát lên ListView
    private void setupSongList() {
        if (musicService == null) return;
        List<String> titles = new ArrayList<>();
        for (Song song : musicService.getSongList()) {
            titles.add(song.getTitle() + " - " + song.getArtist());
        }

        songAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, titles
        ) {
            @NonNull
            @Override
            public android.view.View getView(int position, android.view.View convertView, @NonNull android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                boolean isCurrent = isBound && musicService != null && position == musicService.getCurrentIndex();
                tv.setTextColor(isCurrent ? 0xFF0284C7 : 0xFF1E293B);
                return tv;
            }
        };
        lvSongs.setAdapter(songAdapter);

        lvSongs.setOnItemClickListener((parent, view, position, id) -> {
            if (isBound && musicService != null) {
                startMusicService();
                musicService.playSongAt(position);
            }
        });
    }

    private void startMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    private void updateUI() {
        if (!isBound || musicService == null) return;

        Song song = musicService.getCurrentSong();
        tvSongTitle.setText(song.getTitle());
        tvArtist.setText(song.getArtist());

        if (musicService.isPlaying()) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }

        if (songAdapter != null) {
            songAdapter.notifyDataSetChanged();
        }

        int total = musicService.getDuration();
        if (total > 0) {
            seekBar.setMax(total);
            seekBar.setProgress(musicService.getCurrentPosition());
            tvTotalTime.setText(formatTime(total));
            tvCurrentTime.setText(formatTime(musicService.getCurrentPosition()));
        } else {
            seekBar.setProgress(0);
            tvCurrentTime.setText("00:00");
            tvTotalTime.setText("00:00");
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        100
                );
            }
        }
    }

    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
