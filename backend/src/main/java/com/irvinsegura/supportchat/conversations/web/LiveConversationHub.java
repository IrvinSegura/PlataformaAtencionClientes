package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationSummary;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.MessageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.OperatorMessageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.VisitorConversationView;
import com.irvinsegura.supportchat.conversations.application.LiveUpdates;
import java.io.IOException;
import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps the event streams opened on this instance and pushes to them the changes {@link LiveUpdates} hands over,
 * which come from every instance. Streams end after a while and clients reconnect, which also re-checks their
 * credentials: an operator's stream never outlives their access token.
 */
@Component
class LiveConversationHub implements LiveUpdates.Listener, SmartLifecycle {

    static final Duration OPERATOR_STREAM_TIMEOUT = Duration.ofMinutes(15);
    static final Duration VISITOR_STREAM_TIMEOUT = Duration.ofMinutes(30);
    // A few tabs per visitor and a team's worth of panels; past that, the oldest stream is closed
    private static final int MAX_VISITOR_STREAMS = 3;
    private static final int MAX_OPERATOR_STREAMS = 200;
    // Proxies drop connections that stay silent; a comment line every 20 seconds keeps them open
    private static final long HEARTBEAT_SECONDS = 20;

    private final LiveUpdates live;
    private final Map<UUID, Deque<SseEmitter>> operatorsByWorkspace = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<SseEmitter>> visitorsByConversation = new ConcurrentHashMap<>();
    private ScheduledExecutorService heartbeat;
    private volatile boolean running;
    // The bus keeps its subscribers across a stop and a start, so subscribing again would deliver every event twice
    private boolean subscribed;

    LiveConversationHub(LiveUpdates live) {
        this.live = live;
    }

    SseEmitter subscribeOperator(UUID workspaceId) {
        SseEmitter emitter = register(operatorsByWorkspace, workspaceId, OPERATOR_STREAM_TIMEOUT,
                MAX_OPERATOR_STREAMS);
        send(emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /**
     * The first event is the current state, so a reconnecting widget catches up without another request.
     */
    SseEmitter subscribeVisitor(VisitorConversationView state) {
        SseEmitter emitter = register(visitorsByConversation, state.conversationId(), VISITOR_STREAM_TIMEOUT,
                MAX_VISITOR_STREAMS);
        send(emitter, event("state", state));
        return emitter;
    }

    @Override
    public boolean watchesWorkspace(UUID workspaceId) {
        return operatorsByWorkspace.containsKey(workspaceId);
    }

    @Override
    public boolean watchesConversation(UUID conversationId) {
        return visitorsByConversation.containsKey(conversationId);
    }

    @Override
    public void conversationChanged(UUID workspaceId, ConversationSummary summary, VisitorConversationView state) {
        broadcast(operatorsByWorkspace, workspaceId, "conversation", summary);
        broadcast(visitorsByConversation, summary.id(), "state", state);
    }

    @Override
    public void messageAdded(UUID workspaceId, ConversationSummary summary, OperatorMessageView forOperators,
            MessageView forVisitor) {
        broadcast(operatorsByWorkspace, workspaceId, "message", new OperatorMessage(summary.id(), forOperators));
        // The conversation's activity time moved; lists re-sort on it
        broadcast(operatorsByWorkspace, workspaceId, "conversation", summary);
        if (forVisitor != null) {
            broadcast(visitorsByConversation, summary.id(), "message", forVisitor);
        }
    }

    /**
     * Ends every stream: clients reconnect at once and load the current state, which covers whatever was lost.
     */
    @Override
    public void eventsMissed() {
        completeAll();
    }

    private SseEmitter register(Map<UUID, Deque<SseEmitter>> streams, UUID key, Duration timeout, int max) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        Deque<SseEmitter> subscribers = streams.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
        subscribers.addLast(emitter);
        Runnable remove = () -> {
            subscribers.remove(emitter);
            if (subscribers.isEmpty()) {
                streams.remove(key, subscribers);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> remove.run());
        while (subscribers.size() > max) {
            SseEmitter oldest = subscribers.pollFirst();
            if (oldest != null) {
                oldest.complete();
            }
        }
        return emitter;
    }

    private void broadcast(Map<UUID, Deque<SseEmitter>> streams, UUID key, String name, Object data) {
        Deque<SseEmitter> subscribers = streams.get(key);
        if (subscribers == null) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            send(emitter, event(name, data));
        }
    }

    private static SseEmitter.SseEventBuilder event(String name, Object data) {
        return SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON);
    }

    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // The client went away; completing removes the stream
            emitter.completeWithError(e);
        }
    }

    private void beat() {
        for (Map<UUID, Deque<SseEmitter>> streams : List.of(operatorsByWorkspace, visitorsByConversation)) {
            streams.values().forEach(subscribers -> subscribers.forEach(
                    emitter -> send(emitter, SseEmitter.event().comment("ping"))));
        }
    }

    private void completeAll() {
        operatorsByWorkspace.values().forEach(subscribers -> subscribers.forEach(SseEmitter::complete));
        visitorsByConversation.values().forEach(subscribers -> subscribers.forEach(SseEmitter::complete));
    }

    @Override
    public void start() {
        if (!subscribed) {
            live.listen(this);
            subscribed = true;
        }
        heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("sse-heartbeat").factory());
        heartbeat.scheduleAtFixedRate(this::beat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        running = true;
    }

    /**
     * Ends every stream before the server shuts down, so graceful shutdown does not wait for them to time out.
     */
    @Override
    public void stop() {
        running = false;
        heartbeat.shutdownNow();
        completeAll();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    record OperatorMessage(UUID conversationId, OperatorMessageView message) {
    }
}
