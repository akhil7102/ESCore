package fun.endcore.escoins.economy;

/**
 * Types of coin transactions for auditing and tracking.
 */
public enum TransactionType {
    GIVE,
    TAKE,
    SET,
    TRANSFER_SENT,
    TRANSFER_RECEIVED,
    PURCHASE,
    REFUND
}
