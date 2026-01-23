package ch.battleship.battleshipbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Warms up the WebSocket message broker on application startup.
 *
 * <p>This component sends a dummy message to the WebSocket broker immediately
 * after the application starts. This ensures that all WebSocket infrastructure
 * (channels, registries, executors) is fully initialized before the first real
 * client connects.
 *
 * <p>Without warm-up, the first WebSocket connection can be slow (200-500ms),
 * causing race conditions where events are sent before subscriptions are ready.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketWarmUp {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Triggered when application is fully started and ready to serve requests.
     * Sends a dummy message to warm up the WebSocket broker.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpWebSocketBroker() {
        log.info("🔥 Warming up WebSocket message broker...");

        try {
            // Send dummy message to warm up broker infrastructure
            // This initializes all internal channels and registries
            messagingTemplate.convertAndSend(
                    "/topic/warmup",
                    "WebSocket broker warm-up"
            );

            // Give broker time to fully initialize
            Thread.sleep(500);

            log.info("✅ WebSocket message broker warmed up and ready!");
        } catch (Exception e) {
            log.warn("⚠️ WebSocket warm-up failed (non-critical): {}", e.getMessage());
        }
    }
}