package com.cathy.frauddetection.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cathy.frauddetection.AbstractIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

// No @DataJpaTest here: this class talks to Redis, not Postgres, so there is
// no per-method transaction rollback. Redis state persists across test
// methods within the shared container from AbstractIntegrationTest, which is
// why every test below uses its own unique account id — reusing one would
// let an earlier test's leftover count silently pollute a later assertion.
@org.springframework.boot.test.context.SpringBootTest
@ActiveProfiles("test")
class VelocityServiceTest extends AbstractIntegrationTest {

    @Autowired
    private VelocityService velocityService;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void firstCallReturnsOne() {
        long count = velocityService.recordAndCount("ACC-FIRST-CALL");

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void subsequentCallsIncrementSequentially() {
        String accountId = "ACC-SEQUENTIAL";

        assertThat(velocityService.recordAndCount(accountId)).isEqualTo(1L);
        assertThat(velocityService.recordAndCount(accountId)).isEqualTo(2L);
        assertThat(velocityService.recordAndCount(accountId)).isEqualTo(3L);
    }

    @Test
    void keyIsPrefixedToAvoidCollisionWithOtherRedisUsers() {
        String accountId = "ACC-PREFIX";
        velocityService.recordAndCount(accountId);

        // Confirms the "velocity:" namespace exists as a real key, not just as
        // a comment. If this prefix were ever dropped, a plain "ACC-PREFIX"
        // key would collide with unrelated Redis usage sharing the instance.
        assertThat(redis.hasKey("velocity:" + accountId)).isTrue();
        assertThat(redis.hasKey(accountId)).isFalse();
    }

    // The debt this project has been carrying since Phase 4: TTL is reported
    // in whole seconds, so a single snapshot can't distinguish "EXPIRE ran
    // once" from "EXPIRE ran every time" — both would read close to the full
    // window right after creation. Waiting, then calling again, and checking
    // the TTL kept counting down instead of resetting is the only observation
    // that actually tells the two implementations apart.
    @Test
    void ttlIsSetOnlyOnFirstCallNotOnSubsequentCalls() throws InterruptedException {
        String accountId = "ACC-TTL-DEBT";
        String key = "velocity:" + accountId;

        velocityService.recordAndCount(accountId);
        Long ttlAfterFirstCall = redis.getExpire(key);
        assertThat(ttlAfterFirstCall).isCloseTo(10L, org.assertj.core.data.Offset.offset(1L));

        Thread.sleep(2000);

        // Three more calls. If EXPIRE ran unconditionally on any of these,
        // the TTL below would jump back up near 10 instead of continuing down
        // from where the two-second sleep left it.
        velocityService.recordAndCount(accountId);
        velocityService.recordAndCount(accountId);
        velocityService.recordAndCount(accountId);

        Long ttlAfterSubsequentCalls = redis.getExpire(key);

        assertThat(ttlAfterSubsequentCalls)
                .as("TTL should have kept counting down, not been reset by later calls")
                .isLessThanOrEqualTo(8L);
    }

    @Test
    void differentAccountsHaveIndependentCounts() {
        String accountA = "ACC-INDEPENDENT-A";
        String accountB = "ACC-INDEPENDENT-B";

        velocityService.recordAndCount(accountA);
        velocityService.recordAndCount(accountA);
        velocityService.recordAndCount(accountB);

        assertThat(velocityService.recordAndCount(accountA)).isEqualTo(3L);
        assertThat(velocityService.recordAndCount(accountB)).isEqualTo(2L);
    }
}