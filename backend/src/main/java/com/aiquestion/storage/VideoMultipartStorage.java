package com.aiquestion.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Only the API can initiate or complete an upload. Signed URLs authorize a single numbered part. */
public interface VideoMultipartStorage {
    record Part(int number, long bytes, String etag) {}
    String beginVideo(String key, String type);
    String signVideoPart(String key, String upload, int number);
    void putVideoPart(String key, String upload, int number, InputStream input, long bytes);
    List<Part> videoParts(String key, String upload);
    void finishVideo(String key, String upload, List<Part> parts);
    boolean videoExists(String key);
    long videoLength(String key);
    byte[] videoHeader(String key) throws IOException;
    void abortVideo(String key, String upload);
    List<String> pendingVideoUploads(String key);
}
