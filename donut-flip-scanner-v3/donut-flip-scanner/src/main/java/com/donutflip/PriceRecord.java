package com.donutflip;

/**
 * One observed ASKING price (never a completed sale).
 * page: AH page where it was observed, or -1 when unknown. firstSeen: when this listing was first captured.
 */
public record PriceRecord(String item, int quantity, double price, String attrs, int durabilityPct,
                          long timestamp, int page, long firstSeen) {

    public PriceRecord(String item, int quantity, double price, String attrs, int durabilityPct, long timestamp) {
        this(item, quantity, price, attrs, durabilityPct, timestamp, -1, timestamp);
    }

    public double unitPrice() { return price / Math.max(1, quantity); }

    /** Identity of this exact listing (item, stack, price, traits). Page and time are NOT part of it. */
    public String signature() {
        return item + "|" + quantity + "|" + Math.round(price) + "|" + attrs + "|" + durabilityPct;
    }

    /** Same listing observed again: keeps first-seen, refreshes last-seen and (if known) page. */
    public PriceRecord seenAgain(long ts, int newPage) {
        return new PriceRecord(item, quantity, price, attrs, durabilityPct, ts, newPage > 0 ? newPage : page, firstSeen);
    }
}
