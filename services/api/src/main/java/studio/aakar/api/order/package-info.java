/**
 * Orders (PLAN §6, §11.1): checkout turns the cart into an order awaiting payment, snapshotting items, prices
 * and address; the state machine in {@code OrderLifecycle} moves it through the studio stages and appends to
 * the append-only {@code order_events}, streamed to the tracking page over SSE. Payment outcomes arrive as
 * {@code PaymentSucceeded} / {@code PaymentFailed} events from the payment module; the shipment is booked at
 * {@code packed} through the shipping module; the confirmation message goes through the notification module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Order")
package studio.aakar.api.order;
