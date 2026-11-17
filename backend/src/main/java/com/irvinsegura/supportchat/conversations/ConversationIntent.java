package com.irvinsegura.supportchat.conversations;

/**
 * What a conversation is about, to find and count conversations by topic. A conversation may be about several.
 */
public enum ConversationIntent {
    // Where an order is, its number or its tracking
    ORDER_STATUS,
    // Shipping times, costs and coverage
    SHIPPING,
    // Returns, refunds, exchanges and warranties
    RETURNS,
    // Paying, cards, invoices and charges
    PAYMENTS,
    // Prices, discounts and promotions
    PRICING,
    // Whether a product, size or color is available
    AVAILABILITY,
    // Opening hours, stores and addresses
    HOURS_AND_LOCATION,
    // Signing in, passwords and the customer's account
    ACCOUNT,
    // A complaint about the business
    COMPLAINT
}
