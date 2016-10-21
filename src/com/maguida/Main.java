package com.maguida;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import org.tritonus.sampled.convert.PCM2PCMConversionProvider;

public class Main {

    static private AudioFormat getProcessableFormat(float sampleRate) {
//        float sampleRate = 44100;
//        float sampleRate = 22050;
        int sampleSizeInBits = 8;
        int channels = 1; // mono
        boolean signed = true;
        boolean bigEndian = true;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    private synchronized SourceDataLine getLine(AudioFormat audioFormat)
            throws LineUnavailableException {
        SourceDataLine res = null;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class,
                audioFormat);
        res = (SourceDataLine) AudioSystem.getLine(info);
        res.open(audioFormat);
        return res;
    }

    static private byte[] loadAudioFileAsByteArray(String filePath, float sampleRate)
            throws IOException, UnsupportedAudioFileException {

        System.out.println("Loading file " + filePath);

        File file = new File(filePath);
        AudioInputStream encodedInputStream = AudioSystem.getAudioInputStream(file);
        AudioInputStream decodedInputStream = null;
        AudioInputStream outDin = null;
        PCM2PCMConversionProvider conversionProvider = new PCM2PCMConversionProvider();

        AudioFormat encodedFormat = encodedInputStream.getFormat();
        System.out.println("Encoded format : " + encodedFormat.toString());

        AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                encodedFormat.getSampleRate(), 16 /* sampleSizeInBits */, encodedFormat.getChannels() /*channels */,
                encodedFormat.getChannels() * 2 /* frameSize */, encodedFormat.getSampleRate() /* frameRate */,
                false /* bigEndian */);

        decodedInputStream = AudioSystem.getAudioInputStream(decodedFormat, encodedInputStream);

        AudioFormat processableFormat = getProcessableFormat(sampleRate);
        if (!conversionProvider.isConversionSupported(processableFormat, decodedFormat)) {
            System.out.println("Conversion is not supported");
        }

        System.out.println("Decoded Format : " + decodedFormat.toString());
        System.out.println("ProcessableFormat Format : " + processableFormat.toString());

        outDin = conversionProvider.getAudioInputStream(processableFormat, decodedInputStream);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[(int) 1024];

        while (true) {
            int count = 0;
            count = outDin.read(buffer, 0, 1024);
            if (count > 0) {
                out.write(buffer, 0, count);
            }
            else {
                break;
            }
        }

//        byte b[] = out.toByteArray();
//        for (int i = 0; i < b.length; i++) {
//            System.out.println(b[i]);
//        }
        return out.toByteArray();

    }

    static private byte[] recordLineInAsByteArray(float samplingRate) throws LineUnavailableException    {
        AudioFormat processableFormat = getProcessableFormat(samplingRate);

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, processableFormat);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);

        try {
            line.open(processableFormat);
            line.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }


        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[(int) 1024];

        int nbTotal = 0;
        while (nbTotal < 1024 * 1000) {
            int count = 0;
            count = line.read(buffer, 0, 1024);
            nbTotal += count;

            if (count > 0) {
                out.write(buffer, 0, count);
            }
            else {
                break;
            }
        }

        line.close();

        return out.toByteArray();
    }

    private static final int FUZ_FACTOR = 2;

    static long hash(long p1, long p2, long p3, long p4) {
        /*
        return (p4 - (p4 % FUZ_FACTOR)) * 100000000 + (p3 - (p3 % FUZ_FACTOR))
                * 100000 + (p2 - (p2 % FUZ_FACTOR)) * 100
                + (p1 - (p1 % FUZ_FACTOR));
                */

        long hash = (p4 - (p4 % FUZ_FACTOR)) * 1000000000 + (p3 - (p3 % FUZ_FACTOR))
                * 1000000 + (p2 - (p2 % FUZ_FACTOR)) * 1000
                + (p1 - (p1 % FUZ_FACTOR));

        //System.out.println("" + p1 + "|" + p2 + "|" + p3 + "|" + p4 + "=>" + hash);
        return hash;
    }


    static private Map<Long, List<Integer>> makeSpectrum(byte[] audioSamplesByTime) {
        // return Map<Hash, List<chunkidx>>
        final int CHUNK_SIZE = 4096; // EBO : is it big enough ?
        int totalSize = audioSamplesByTime.length;
        int nbChunks = totalSize / CHUNK_SIZE;

        System.out.println("nbChunks=" +nbChunks);

        /*
        short[] shorts = new short[audioSamplesByTime.length/2];
        // to turn bytes to shorts as either big endian or little endian.
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        */


        // When turning into frequency domain we'll need complex numbers:
        com.maguida.Complex[][] audioChunksByFrequencies = new com.maguida.Complex[nbChunks][];

        // For all the chunks:
        for (int chunk = 0; chunk < nbChunks; chunk++) {
            Complex[] chunkSamplesByTimeAsComplexArray = new Complex[CHUNK_SIZE];
            for (int i = 0; i < CHUNK_SIZE; i++) {
                // Put the time domain data into a chunkSamplesByTimeAsComplexArray number with imaginary part as 0:
                chunkSamplesByTimeAsComplexArray[i] = new Complex(audioSamplesByTime[(chunk * CHUNK_SIZE) + i], 0);
            }
            // Perform FFT analysis on the chunk (return an array[freq] = amplitude)
            audioChunksByFrequencies[chunk] = com.maguida.FFT.fft(chunkSamplesByTimeAsComplexArray);
        }

        return determineKeyPoints(audioChunksByFrequencies);
    }

    static private Map<Long, List<Integer>> determineKeyPoints(Complex[][] results) {
        // return Map<Hash, List<chunkidx>>
        //this.matchMap = new HashMap<Integer, Map<Integer, Integer>>();

        double highestMagnitudeByFreqRange[][];
        double recordPoints[][];
        long freqWithHighestMagnitudeByFreqRange[][];
        Map<Long, List<Integer>> hashMap = new HashMap<Long, List<Integer>>();

        // highestMagnitudeByFreqRange[chunkIdx][rangeIdx] = highest mag
        highestMagnitudeByFreqRange = new double[results.length][NB_FREQ_RANGES];
        for (int chunk = 0; chunk < results.length; chunk++) {
            for (int j = 0; j < NB_FREQ_RANGES; j++) {
                highestMagnitudeByFreqRange[chunk][j] = 0;
            }
        }

        /*
        recordPoints = new double[results.length][FREQ_UPPER_LIMIT];
        for (int chunk = 0; chunk < results.length; chunk++) {
            for (int j = 0; j < FREQ_UPPER_LIMIT; j++) {
                recordPoints[chunk][j] = 0;
            }
        }
        */

        // freqWithHighestMagnitudeByFreqRange[chunkIdx][rangeIdx] = freq having the highest mag
        freqWithHighestMagnitudeByFreqRange = new long[results.length][NB_FREQ_RANGES];
        for (int chunk = 0; chunk < results.length; chunk++) {
            for (int j = 0; j < NB_FREQ_RANGES; j++) {
                freqWithHighestMagnitudeByFreqRange[chunk][j] = 0;
            }
        }

        for (int chunk = 0; chunk < results.length; chunk++) {
            for (int freq = FREQ_LOWER_LIMIT; freq < FREQ_UPPER_LIMIT - 1; freq++) {

                // Get the magnitude (re*re + im*im) for the current freq
                double mag = Math.log(results[chunk][freq].abs() + 1);

                // Find out which range we are in:
                int freqRangeIndex = getFreqRangeIndexFromFreq(freq);

                // Save the highest magnitude and corresponding frequency:
                if (mag > highestMagnitudeByFreqRange[chunk][freqRangeIndex]) {
                    highestMagnitudeByFreqRange[chunk][freqRangeIndex] = mag;
//                    recordPoints[chunk][freq] = 1;
                    freqWithHighestMagnitudeByFreqRange[chunk][freqRangeIndex] = freq;
                }
            }

            /*
            try {
                for (int freqRangeIndex = 0; freqRangeIndex < NB_FREQ_RANGES; freqRangeIndex++) {
                    outFile.write("" + highestMagnitudeByFreqRange[chunk][freqRangeIndex] + ";"
                            + recordPoints[chunk][freqRangeIndex] + "\t");
                }
                outFile.write("\n");

            } catch (IOException e) {
                e.printStackTrace();
            }
            */

            long h = hash(freqWithHighestMagnitudeByFreqRange[chunk][0], freqWithHighestMagnitudeByFreqRange[chunk][1], freqWithHighestMagnitudeByFreqRange[chunk][2], freqWithHighestMagnitudeByFreqRange[chunk][3]);

            List<Integer> listPoints = null;
            listPoints = hashMap.get(h);
            if (listPoints == null) {
                listPoints = new ArrayList<Integer>();
                hashMap.put(h, listPoints);
            }

            listPoints.add(chunk);
        }

        /*
        try {
            outFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
        return hashMap;
    }

    public static final int FREQ_UPPER_LIMIT = 300;
    public static final int FREQ_LOWER_LIMIT = 40;
    //frequency ranges are : [0-40] - ]40-80] - ]80-120] - ]120-180] - ]180-301]
    public static final int[] FREQ_RANGES = new int[] { 40, 80, 120, 180, FREQ_UPPER_LIMIT + 1 };
    public static final int NB_FREQ_RANGES = FREQ_RANGES.length;

    // Find out in which range
    public static int getFreqRangeIndexFromFreq(int freq) {
        int i = 0;
        while (FREQ_RANGES[i] < freq)
            i++;
        return i;
    }

    public static void addSongSpectrumToDB(Map<Long, List<DataPoint>> hashDB, Map<Long, List<Integer>> fileHashMap, int songId)
    {
        for (Map.Entry<Long, List<Integer>> entry : fileHashMap.entrySet()) {
            List<DataPoint> listPoints = null;
            if ((listPoints = hashDB.get(entry.getKey())) == null) {
                listPoints = new ArrayList<DataPoint>();
                hashDB.put(entry.getKey(), listPoints);
            }

            for (Integer chunk : entry.getValue()) {
                DataPoint point = new DataPoint((int) songId, chunk);
                listPoints.add(point);
            }
        }
    }



    public static List<MatchingResult> findMatches(Map<Long, List<DataPoint>> hashDB, Map<Long, List<Integer>> microHashMap) {
        //<songid, MatchingResult>
        Map<Integer, MatchingResult> matches = new HashMap<Integer, MatchingResult>();
        List<MatchingResult> matchingResults = new ArrayList<MatchingResult>();

        for (Map.Entry<Long, List<Integer>> entry : microHashMap.entrySet()) {
            Long hash = entry.getKey();
            List<Integer> listPoints = entry.getValue();
            /*
            System.out.println("Hash :" + hash);
            for (Integer chunk : listPoints) {
                System.out.println("\tChunk=" + chunk);
            }
            */
            List<DataPoint> listPointsInDB = hashDB.get(hash);
            if (listPointsInDB != null) {
                System.out.println("Hash " + hash.toString() + " (which is present " + listPoints.size() + " times in the song) matched a hash in the DB");
                for (DataPoint dataPoint : listPointsInDB) {
                    Integer songId = new Integer(dataPoint.getSongId());
                    System.out.println("\tSongId=" + dataPoint.getSongId() + " chunk=" + dataPoint.getTime());
                    MatchingResult matchingResult = matches.get(songId);
                    if (matchingResult == null) {
                        matchingResult = new MatchingResult(songId);
                        matches.put(songId, matchingResult);
                    }

                    for (Integer chunk : listPoints) {
                        matchingResult.addMatchingChunk(dataPoint.getTime(), chunk);
                    }

                }
            }
        }

        for (Map.Entry<Integer, MatchingResult> entry : matches.entrySet()) {
            MatchingResult matchingResult = entry.getValue();
            System.out.println("\tSongId=" + matchingResult.getRefSongId() + " matched " + matchingResult.getNbMatches() + " times");
            matchingResults.add(matchingResult);
        }

        Collections.sort(matchingResults, new Comparator<MatchingResult>() {
            public int compare(MatchingResult r2, MatchingResult r1)
            {
                if (r1.getNbMatches() > r2.getNbMatches())
                    return 1;
                if (r1.getNbMatches() < r2.getNbMatches())
                    return -1;
                return 0;
            }
        });

        for (MatchingResult mr : matchingResults) {
            System.out.println("\tmatched SongId=" + mr.getRefSongId());

            // sort on chunk on samples
            Collections.sort(mr.chunkPairs, new Comparator<ChunkPair>() {
                public int compare(ChunkPair r2, ChunkPair r1)
                {
                    if (r1.getChunkInSample() < r2.getChunkInSample())
                        return 1;
                    if (r1.getChunkInSample() > r2.getChunkInSample())
                        return -1;
                    return 0;
                }
            });

            for (ChunkPair cp : mr.chunkPairs) {
                System.out.println("\tSampleChunk=" + cp.getChunkInSample() + " RefChunk=" + cp.getChunkInRef());
            }
        }


        return matchingResults;
    }

    public static List<String> getAudioFilesInDir(String path) {
        List<String> results = new ArrayList<String>();
        File dir = null;

        try {
            // create new file
            dir = new File(path);

            // create new filename filter
            FilenameFilter fileNameFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (name.lastIndexOf('.') > 0) {
                        // get last index for '.' char
                        int lastIndex = name.lastIndexOf('.');

                        // get extension
                        String ext = name.substring(lastIndex);

                        // match path name extension
                        if (ext.equalsIgnoreCase(".mp3") || ext.equalsIgnoreCase(".wav")) {
                            return true;
                        }
                    }
                    return false;
                }
            };

            File[] files = dir.listFiles(fileNameFilter);
            for (File file : files) {
                if (file.isFile()) {
                    // local file name
                    //file.getName()
                    results.add(file.getAbsolutePath());
                }
            }
        }catch(Exception e){
            // if any error occurs
            e.printStackTrace();
        }

        return results;
    }

    public static void main(String[] args) {
        Map<Long, List<DataPoint>> hashDB = new HashMap<Long, List<DataPoint>>();

        Map<Integer, Song> songs = new HashMap<Integer, Song>();
        float sampleRate;

        sampleRate = 44100;
        //List<String> audioFiles = getAudioFilesInDir("/home/ebo/IdeaProjects/audiofp/reference/birds/");
        /*
        //http://www.universal-soundbank.com/oiseaux.htm
        String[] files = {
                "/home/ebo/IdeaProjects/audiofp/reference/aigle-royal-4046.wav",
//                "/home/ebo/IdeaProjects/audiofp/reference/canaris-1676.wav",
//                "/home/ebo/IdeaProjects/audiofp/reference/chardonneret-12623.wav", //48000.0
                "/home/ebo/IdeaProjects/audiofp/reference/chouette1-153.wav",
//                "/home/ebo/IdeaProjects/audiofp/reference/colombe-12602.wav", //48000;0
                "/home/ebo/IdeaProjects/audiofp/reference/corbeau-qui-passe-dans-le-ciel-8076.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/corneille-22363.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/coucou-1-4037.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/coucou-2-4038.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/coucou-3-10628.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/faisan-cri-alerte-4027.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/faucon-crecerelle-1-2378.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/geais-3-22208.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/grive-musicienne-22215.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/hirondelle-3-1222.wav",
//                "/home/ebo/IdeaProjects/audiofp/reference/merle-12609.wav", //48000
//                "/home/ebo/IdeaProjects/audiofp/reference/merle-2-12610.wav", //48000
                "/home/ebo/IdeaProjects/audiofp/reference/mesange-tete-bleue-22213.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/mouette-1-168.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/mouette-2-169.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/mouette-rieuse-8089.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/oies-canada-a-terre-8085.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/paon-2-2213.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/paon-3-11978.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/paon-32.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/perroquet-1-2369.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/perruche-2382.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/pic-vert-2-3244.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/pie-1-1220.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/pie-2-4042.wav",
//                "/home/ebo/IdeaProjects/audiofp/reference/toucan-cri-12624.wav", //48000
                "/home/ebo/IdeaProjects/audiofp/reference/tourterelle-1-197.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/tourterelle-2-191.wav",
                "/home/ebo/IdeaProjects/audiofp/reference/tourterelle-3-8100.wav"
        };
        */

        sampleRate = 44100;
        List<String> audioFiles = getAudioFilesInDir("/home/ebo/IdeaProjects/audiofp/reference/zic/");
        /*
        String[] files = {
                "/home/ebo/IdeaProjects/audiofp/reference/Wake-Me-Up.mp3",
                "/home/ebo/IdeaProjects/audiofp/reference/Hey-Brother.mp3",
                "/home/ebo/IdeaProjects/audiofp/reference/Magic_System@Africainement_votre@Magic_In_The_Air.mp3",
                "/home/ebo/IdeaProjects/audiofp/reference/Queen@Live_at_Wembley_86@We_Are_the_Champions.mp3",
                "/home/ebo/IdeaProjects/audiofp/reference/Nirvana@Nevermind@Come_As_You_Are.mp3",
                "/home/ebo/IdeaProjects/audiofp/reference/AC-DC@Live@Highway_To_Hell.mp3",
                "/home/ebo/IdeaProjects/audiofp/reference/Alex_Hepburn@Under@Under.mp3",
        };
        */

        for (String file : audioFiles) {
            Song s = new Song(songs.size()+ 1, file);
            songs.put(new Integer(s.getSongId()), s);
        }

        System.out.println("Loading + fingerprinting songs");
        for (Map.Entry<Integer, Song> entry : songs.entrySet()) {
            Song s = entry.getValue();

            // Map<hash,List<chunkidx>>
            Map<Long, List<Integer>> fileHashMap =  new HashMap<Long, List<Integer>>();

            System.out.println("Loading song " + s.getPath());
            if (s.loadSpectrum(fileHashMap)) {
                System.out.println("\tLoaded spectrum from spectrum file");
            }
            else
            {
                System.out.println("\tspectrum file does not exist => building it");

                byte fileByteArray[] = null;
                try {
                    fileByteArray = loadAudioFileAsByteArray(s.getPath(), sampleRate);
                } catch (IOException e1) {
                    e1.printStackTrace();
                } catch (UnsupportedAudioFileException e1) {
                    e1.printStackTrace();
                }
                fileHashMap= makeSpectrum(fileByteArray);
                s.saveSpectrum(fileHashMap);
            }

            addSongSpectrumToDB(hashDB, fileHashMap, s.getSongId());
        }

        System.out.println("Recording microphone");
        byte microByteArray[] = null;
        try {

            microByteArray = recordLineInAsByteArray(sampleRate);
        } catch (LineUnavailableException ex) {
            ex.printStackTrace();
        }
        /*
        try {
            microByteArray = loadAudioFileAsByteArray("/home/ebo/IdeaProjects/audiofp/reference/tourterelle-3-8100.wav", sampleRate);
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (UnsupportedAudioFileException e1) {
            e1.printStackTrace();
        }
        */

        //Map<hash,List<chunkidx>>
        Map<Long, List<Integer>> microHashMap = makeSpectrum(microByteArray);


//        for (int i = 0; i < microByteArray.length; i++) {
//            System.out.println(fileByteArray[i]);
//        }

        /*
        for (Map.Entry<Long, List<DataPoint>> entry : hashDB.entrySet()) {
            Long hash = entry.getKey();
            List<DataPoint> listPoints = entry.getValue();

            // dump hash DB
            System.out.println("Hash :" + hash);
            for (DataPoint dataPoint : listPoints) {
                System.out.println("\tSongId=" + dataPoint.getSongId() + " Chunk=" + dataPoint.getTime());
            }

        }
        */

        System.out.println("=========== Record Hashes :");
        int nb = 0;
        for (Map.Entry<Long, List<Integer>> entry : microHashMap.entrySet()) {
            Long hash = entry.getKey();
            List<Integer> listPoints = entry.getValue();
            System.out.println("Hash :" + hash);
            for (Integer chunk : listPoints) {
                System.out.println("\tChunk=" + chunk);
                nb++;
            }
        }
        System.out.println("Record has " + nb + " Hashes :");

        List<MatchingResult> matchingResults = findMatches(hashDB, microHashMap);

        if (matchingResults.size() > 0) {
            for (MatchingResult mr : matchingResults) {
                Song s = songs.get(mr.getRefSongId());
                System.out.println("\tmatched SongId=" + s.getSongId() + " (" + s.getTitle() + ") " + mr.getNbMatches() + " matches");
            }
        }
    }
}
