# ADR-0013: Complete the customer loop with external providers mocked behind adapters

- Status: Accepted (decided by the product owner on 2026-09-27)
- Date: 2026-09-27
- Deciders: Chandan Bharadwaj (product owner)

## Context
Phase 1 must deliver the whole customer loop (sign in, cart, checkout, order, tracking, unboxing) so the product can be played with locally. Payments (Razorpay), shipping (Delhivery), messaging (WhatsApp Cloud API) and SMS OTP are external dependencies with account setup, compliance and cost; the owner wants the product ready first and the integrations later.

## Decision
Every external dependency sits behind an **adapter interface** in the API with a **mock implementation** that is the default in local profiles, plus a **placeholder page** in the storefront or portal that stands in for the provider's own UI:

| Dependency | Interface | Mock behaviour | Placeholder |
|---|---|---|---|
| Payments | `PaymentGateway` | Creates a mock payment; a "Pay" page lets the tester choose success or failure; confirms through the same webhook path a real gateway would use | `/checkout/pay/[id]` mock gateway page |
| Shipping | `ShippingCarrier` | Serviceability for any 6-digit pincode, flat ETA (4 days), fake AWB and tracking events advanced by staff | Carrier events shown in tracking |
| Messaging | `MessageSender` (WhatsApp, email) | Records outbound messages in a `notifications` log instead of sending | Portal "Messages" page shows what would have been sent |
| OTP / SMS | `OtpSender` | Logs the code and, in the `local` profile, returns it to the sign-in page | Sign-in page shows the dev code |
| Printing | outsourced (ADR-0004 deferred) | No integration; staff download a print pack and advance stages by hand | Portal fulfilment queue |

Real adapters are added later without touching callers; the profile selects the implementation.

## Consequences
- The loop can be exercised end to end on a laptop with no accounts or secrets.
- Mock pages must be unmistakably marked as mocks and must never be enabled in a production profile (a startup check refuses `mock` adapters when `aakar.profile=production`).
- Data models (payments, shipments, notifications) are designed for the real providers so integration is an adapter swap, not a schema change.
