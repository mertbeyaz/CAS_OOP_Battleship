package ch.battleship.battleshipbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket / STOMP configuration for real-time communication.
 *
 * <p>This configuration enables a simple in-memory message broker for server-to-client
 * events (topics) and defines the STOMP endpoint used by the web client to establish
 * the WebSocket connection.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configures the message broker routing.
     *
     * <p>Conventions:
     * <ul>
     *   <li><code>/topic</code> is used for server-to-client broadcasts (publish/subscribe).</li>
     *   <li><code>/app</code> is used for client-to-server messages mapped to application handlers.</li>
     * </ul>
     *
     * @param config the broker registry provided by Spring
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Server -> Client Topics
        config.enableSimpleBroker("/topic");

        // Client -> Server destinations (e.g. @MessageMapping handlers)
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers STOMP endpoints used to establish WebSocket connections.
     *
     * <p>Notes:
     * <ul>
     *   <li><code>/ws</code> is the endpoint URL used for the WebSocket upgrade.</li>
     *   <li>SockJS is enabled as an optional fallback for environments without native WebSocket support.</li>
     *   <li><code>setAllowedOriginPatterns("*")</code> is used for development/testing and should be
     *       restricted for production deployments.</li>
     * </ul>
     *
     * @param registry the STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        // DEV only: allows WebSocket connections from any frontend origin.
        // In production, restrict to the frontend origin(s), e.g. https://www.battleship.ch

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        // Enables SockJS as a fallback transport to ensure real-time communication
        // even if native WebSocket connections are blocked.
    }
}
