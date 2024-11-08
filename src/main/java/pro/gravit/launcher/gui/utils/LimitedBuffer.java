package pro.gravit.launcher.gui.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class LimitedBuffer {
    private final byte[] buffer;
    private final AtomicInteger pos = new AtomicInteger();

    public LimitedBuffer(byte[] buffer) {
        this.buffer = buffer;
    }

    public LimitedBuffer(int limit) {
        this.buffer = new byte[limit];
    }

    public byte[] get() {
        return buffer;
    }

    public void clear() {
        pos.set(0);
    }

    public void accessAndClear(BufferAccess callback) {
        synchronized (buffer) {
            callback.access(buffer, 0, pos.get());
            clear();
        }
    }

    public boolean put(byte[] buf, int off, int len) {
        boolean overflow = false;
        synchronized (buffer) {
            if(pos.get() + len > buffer.length) {
                len = buffer.length - pos.get();
                overflow = true;
            }
            System.arraycopy(buf, off, buffer, pos.get(), len);
            pos.addAndGet(len);
        }
        return overflow;
    };

    @FunctionalInterface
    public interface BufferAccess {
        void access(byte[] buf, int off, int len);
    }
}
