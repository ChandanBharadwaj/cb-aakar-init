/**
 * Outbound customer messages (PLAN §12, ADR-0013) through the {@link studio.aakar.api.notification.MessageSender}
 * adapter. Every message is recorded in {@code notifications}; {@code aakar.messaging.sender=log} (default) only
 * logs and records it with status {@code logged}. A WhatsApp Cloud API sender is a drop-in implementation.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package studio.aakar.api.notification;
