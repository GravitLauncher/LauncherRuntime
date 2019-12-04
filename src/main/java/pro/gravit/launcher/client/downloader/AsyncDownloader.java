package pro.gravit.launcher.client.downloader;

public class AsyncDownloader {
    public static class SizedFile
    {
        public final String path;
        public final long size;

        public SizedFile(String path, long size) {
            this.path = path;
            this.size = size;
        }
    }
}
