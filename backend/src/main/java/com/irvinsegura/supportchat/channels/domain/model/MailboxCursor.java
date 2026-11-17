package com.irvinsegura.supportchat.channels.domain.model;

/**
 * How far a mailbox was read. IMAP numbers each message with a UID that only grows, valid as long as the mailbox keeps
 * its UIDVALIDITY.
 *
 * @param lastUid the last message read
 */
public record MailboxCursor(long uidValidity, long lastUid) {
}
