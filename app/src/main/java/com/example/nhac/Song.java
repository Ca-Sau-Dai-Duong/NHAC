package com.example.nhac;

public class Song {
    private final String title;
    private final String artist;
    private final int resId; // ID file nhạc trong res/raw

    public Song(String title, String artist, int resId) {
        this.title = title;
        this.artist = artist;
        this.resId = resId;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public int getResId() {
        return resId;
    }
}
