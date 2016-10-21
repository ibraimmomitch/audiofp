package com.maguida;

import java.util.ArrayList;
import java.util.List;

public class MatchingResult {
    private int refSongId;
    public List<ChunkPair> chunkPairs;

    public MatchingResult(int refSongId) {
        this.refSongId = refSongId;
        this.chunkPairs = new ArrayList<ChunkPair>();
    }

    public int getRefSongId() {
        return this.refSongId;
    }

    public int getNbMatches() {
        return this.chunkPairs.size();
    }


    public void addMatchingChunk(int chunkInRef, int chunkInSample) {
        this.chunkPairs.add(new ChunkPair(chunkInRef, chunkInSample));
    }

}