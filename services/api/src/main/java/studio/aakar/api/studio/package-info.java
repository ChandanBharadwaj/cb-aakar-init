/**
 * Generation jobs: accept, dispatch to the geometry service (direct HTTP or RabbitMQ), track stages,
 * apply results and stream progress over SSE. Publishes {@link studio.aakar.api.studio.GenerationCompleted}
 * and {@link studio.aakar.api.studio.GenerationFailed} for the design module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Studio")
package studio.aakar.api.studio;
