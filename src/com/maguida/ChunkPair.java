package com.maguida;

/**
 * Created by ebo on 10/10/16.
 */
public class ChunkPair {
    private int chunkInRef;
    private int chunkInSample;

    public ChunkPair(int chunkInRef, int chunkInSample) {
        this.chunkInRef = chunkInRef;
        this.chunkInSample = chunkInSample;
    }

    public int getChunkInRef(){
        return this.chunkInRef;
    }

    public int getChunkInSample(){
        return this.chunkInSample;
    }
}
