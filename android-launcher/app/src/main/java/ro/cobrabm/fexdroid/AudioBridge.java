package ro.cobrabm.fexdroid;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Game audio: SDL's disk driver writes the mixed output (s16 stereo 48 kHz, run-gow3.sh) into a
 *  FIFO; this plays it. The FIFO is held open read-write, so the game's open never blocks and a
 *  restarted game writes into the same pipe. The playback rate paces the game's audio thread. */
final class AudioBridge {
    private static final int RATE=48000, FRAME=4;
    private static final int F_SETPIPE_SZ=1031;
    private final File fifo;
    private final File ready;
    private volatile boolean running;
    private volatile AudioTrack track;
    private volatile RandomAccessFile pipeHandle;
    private volatile int state; // 0 starting, 1 ready, -1 failed/stopped

    AudioBridge(File fifo) { this.fifo=fifo; this.ready=new File(fifo.getPath()+".ready"); }

    void start() {
        if (running) return;
        ready.delete();
        state=0;
        running=true;
        Thread thread=new Thread(this::run,"God of War III-audio");
        thread.setPriority(Thread.MAX_PRIORITY); thread.setDaemon(true); thread.start();
    }
    void stop() {
        running=false; state=-1; ready.delete();
        AudioTrack t=track; if (t!=null) t.pause();
        RandomAccessFile p=pipeHandle;
        if (p!=null) try { p.close(); } catch (IOException ignored) {}
    }
    void pause() { AudioTrack t=track; if (t!=null) t.pause(); }
    void resume() { AudioTrack t=track; if (t!=null) t.play(); }

    boolean awaitReady(long timeoutMs) {
        long deadline=System.currentTimeMillis()+timeoutMs;
        while (state==0 && System.currentTimeMillis()<deadline) {
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return state==1 && running && ready.isFile();
    }

    private void run() {
        AudioTrack active=null;
        try {
            File parent=fifo.getParentFile();
            if (parent!=null) parent.mkdirs();
            if (fifo.exists() && !isFifo(fifo)) fifo.delete();
            if (!fifo.exists()) Os.mkfifo(fifo.getPath(),0600);
            try (RandomAccessFile pipe=new RandomAccessFile(fifo,"rw");
                 FileInputStream in=new FileInputStream(pipe.getFD())) {
                pipeHandle=pipe;
                FileDescriptor fd=pipe.getFD();
                // A small pipe keeps the delay short (~40 ms); the default 64 KiB is ~340 ms.
                try { Os.fcntlInt(fd,F_SETPIPE_SZ,8192); } catch (Exception ignored) {}
                int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
                AudioTrack t=new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(Math.max(min,RATE*FRAME/20))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
                if (t.getState()!=AudioTrack.STATE_INITIALIZED) throw new IOException("AudioTrack is not initialized");
                active=t;
                track=t; t.play();
                if (t.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING) throw new IOException("AudioTrack did not start");
                if (!ready.createNewFile()) throw new IOException("audio ready marker already exists");
                state=1;
                android.util.Log.i("God of War III","audio: ready (48 kHz, s16 stereo)");
                byte[] buffer=new byte[4096];
                int carry=0;
                while (running) {
                    int n=in.read(buffer,carry,buffer.length-carry);
                    if (n<0) break;
                    if (n==0) { Thread.sleep(5); continue; }
                    int whole=(carry+n)/FRAME*FRAME;
                    int written=0;
                    while (written<whole && running) {
                        int result=t.write(buffer,written,whole-written,AudioTrack.WRITE_BLOCKING);
                        if (result<=0) throw new IOException("AudioTrack.write failed: "+result);
                        written+=result;
                    }
                    carry=carry+n-whole;
                    if (carry>0) System.arraycopy(buffer,whole,buffer,0,carry);
                }
            }
        } catch (Exception ex) {
            boolean unexpected=running;
            state=-1; running=false; ready.delete();
            if (unexpected) android.util.Log.e("God of War III","audio bridge failed: "+ex);
        } finally {
            if (active!=null) {
                try { active.stop(); } catch (Exception ignored) {}
                active.release(); track=null;
            }
            pipeHandle=null;
            if (!running) ready.delete();
        }
    }
    private static boolean isFifo(File file) {
        try { return OsConstants.S_ISFIFO(Os.stat(file.getPath()).st_mode); } catch (Exception e) { return false; }
    }
}
