package com.donutflip;

/** One manually recorded / imported asking-price observation. NOT a completed sale. */
public record PriceRecord(String item, int quantity, double price, String attrs, int durabilityPct, long timestamp) {
    public double unitPrice() { return price / Math.max(1, quantity); }
}
