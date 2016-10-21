package com.maguida;

public class DataPoint {
	private int songId;
	private int time;

	public DataPoint(int songId, int time) {
		this.songId = songId;
		this.time = time;
	}

	public int getTime() {
		return time;
	}

	public int getSongId() {
		return songId;
	}
}
