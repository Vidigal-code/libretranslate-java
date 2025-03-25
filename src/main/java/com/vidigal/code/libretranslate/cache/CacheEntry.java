package com.vidigal.code.libretranslate.cache;

/**
 * Inner class to CacheEntry.
 */

public class CacheEntry {

    private final String value;
    private final long timestamp;

    public CacheEntry(String value) {
        this.value = value;
        this.timestamp = System.currentTimeMillis();
    }

    public String getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }
}