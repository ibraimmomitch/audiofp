package com.maguida;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ebo on 09/10/16.
 */
public class Song {
    private int songId;
    private String title;
    private String path;

    public Song(int songId, String path) {
        this.songId = songId;
        this.path = path;
        this.title = path.substring(path.lastIndexOf("/") + 1);
    }

    public int getSongId() {
        return this.songId;
    }

    public String getTitle() {
        return this.title;
    }

    public String getPath() {
        return this.path;
    }

    public String getFpPath() {
        return this.path + ".txt";
    }

    public boolean saveSpectrum(Map<Long, List<Integer>> fileHashMap) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(getFpPath()));

            try{
                for (Map.Entry<Long, List<Integer>> entry : fileHashMap.entrySet()) {
                    StringBuffer b = new StringBuffer();
                    boolean first = true;

                    b.append(entry.getKey().toString());
                    b.append("=");
                    for (Integer chunk : entry.getValue()) {
                        if (first)
                            first = false;
                        else
                            b.append(",");

                        b.append(chunk.toString());
                    }
                    writer.write(b.toString());
                    writer.write("\n");

                }
                writer.close();
            }
            finally {
                //no need to check for null
                //any exceptions thrown here will be caught by
                //the outer catch block
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("I/O problems: " + e);
            //System.exit(-1);
            return false;
        }
        return true;
    }

    public boolean loadSpectrum(Map<Long, List<Integer>> fileHashMap) {

        //Map<Long, List<Integer>> hashMap = new HashMap<Long, List<Integer>>();

        File f = new File(getFpPath());
        if (!f.isFile())
            return false;

        try {

            BufferedReader reader = new BufferedReader(new FileReader(getFpPath()));
            try {
                String line = null;
                // parse hash=chunk1,chunk2....
                while ((line = reader.readLine()) != null) {
                    String tokens[] =  line.split("=");
                    if (tokens.length != 2)
                        continue;

                    Long hash;
                    try {
                        hash = Long.parseLong(tokens[0]);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }

                    List<Integer> listPoints = fileHashMap.get(hash);
                    if (listPoints == null) {
                        listPoints = new ArrayList<Integer>();
                        fileHashMap.put(hash, listPoints);
                    }

                    for (String chunkStr: tokens[1].split(",")) {
                        Integer chunk;
                        try {
                            chunk = Integer.parseInt(chunkStr);
                        } catch (NumberFormatException nfe) {
                            continue;
                        }

                        listPoints.add(chunk);
                    }
                }
            }
            finally {
                //no need to check for null
                //any exceptions thrown here will be caught by
                //the outer catch block
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("I/O problems: " + e);
            //System.exit(-1);
            return false;
        }
        return true;
    }

}
