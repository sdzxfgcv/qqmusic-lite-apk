package com.ima.qqmusiclite;

public class Song {
    public String mid = "";
    public String name = "";
    public String singer = "";
    public String albumMid = "";
    public int interval = 0;

    public Song() {}

    @Override
    public String toString() {
        return name + " - " + singer;
    }
}
