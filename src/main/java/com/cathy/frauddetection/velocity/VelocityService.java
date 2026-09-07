package com.cathy.frauddetection.velocity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class VelocityService {

    // Redis is one shared keyspace. Phase 5 adds rule caching to the same
    // instance; without a namespace prefix the two uses can collide on a key.
    private static final String KEY_PREFIX = "velocity:";

    private final StringRedisTemplate redis;
    private final Duration window;

    // Single constructor, so @Autowired is not needed. Constructor injection
    // keeps the fields final and lets a unit test build this with plain new.
    public VelocityService(StringRedisTemplate redis,
                           @Value("${fraud.velocity.window}") Duration window) {
        this.redis = redis;
        this.window = window;
    }

    // recordAndCount, not getCount: this mutates. A caller who reads the name
    // as a getter and calls it twice inflates the count with no error anywhere.
    public long recordAndCount(String accountId) {
        String key = KEY_PREFIX + accountId;

        // INCR is atomic and returns the new value, so a single round trip
        // answers both "how many" and "did this key just get created".
        // EXISTS-then-SET would be check-then-act: two clients can both see
        // "absent", both write 1, and one transaction is lost.
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            // Only reachable in pipeline or transaction mode, which this is not.
            // Returning 0 instead would silently fail open on a fraud check.
            throw new IllegalStateException("INCR returned no value for " + key);
        }

        if (count == 1L) {
            // TTL is set on creation only. Expiring on every call pushes the
            // deadline forward forever, so a busy account's counter never
            // resets and the rule, once tripped, stays tripped.
            redis.expire(key, window);
        }

        return count;
    }
}