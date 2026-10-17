package com.donutflip;

/** One manually recorded / imported asking-price observation. NOT a completed sale. */
public record PriceRecord(String item, int quantity, double price, String attrs, int durabilityPct, long timestamp, int page) {
    /** Keeps compatibility with existing import and captured CSV records. */
    public PriceRecord(String item, int quantity, double price, String attrs, int durabilityPct, long timestamp) {
        this(item, quantity, price, attrs, durabilityPct, timestamp, -1);
    }
    public double unitPrice() { return price / Math.max(1, quantity); }
}
