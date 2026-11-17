package com.irvinsegura.supportchat.shared.web;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;

/**
 * Buckets in Redis, shared by every instance, so a client gets each limit once however many instances serve it. A
 * bucket is updated with compare-and-swap, so two instances taking a token at once never both get the last one, and
 * it expires once it would be full again.
 */
class RedisRateLimitBuckets implements RateLimitBuckets, DisposableBean {

    private static final String PREFIX = "supportchat:rate-limit:";

    // A request waits at most this long for Redis; past it the filter lets the request through (see RateLimitFilter)
    private static final Duration TIMEOUT = Duration.ofMillis(500);

    // A full bucket is kept this much longer, so a client that comes back right away still finds it
    private static final Duration KEEP_FULL = Duration.ofSeconds(10);

    private final RedisClient client;
    private final StatefulRedisConnection<String, byte[]> connection;
    private final ProxyManager<String> buckets;

    RedisRateLimitBuckets(String url) {
        this.client = RedisClient.create(url);
        this.connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        connection.setTimeout(TIMEOUT);
        this.buckets = Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(KEEP_FULL))
                .requestTimeout(TIMEOUT)
                .build();
    }

    @Override
    public Bucket bucket(String key, long capacity, Duration period) {
        // The limit is part of the key: after a deploy that changes it, clients start from a bucket of the new size
        return buckets.builder().build(PREFIX + key + "|" + capacity + "/" + period,
                () -> BucketConfiguration.builder()
                        .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                        .build());
    }

    @Override
    public void destroy() {
        connection.close();
        client.shutdown();
    }
}
