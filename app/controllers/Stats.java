package controllers;

import com.forwardcat.common.RedisKeys;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Result;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import views.html.stats;

import static models.JedisHelper.returnJedisOnException;

public class Stats extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(Stats.class.getName());
    private final JedisPool jedisPool;

    @Inject
    Stats(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public Result render() {
        int activeProxies;
        int emailsBlocked;
        int emailsForwarded;
        int proxiesActivated;
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            Pipeline pipeline = jedis.pipelined();

            Response<Long> activeProxiesRs = pipeline.zcard(RedisKeys.ALERTS_SET);
            Response<String> emailsBlockedRs = pipeline.get(RedisKeys.EMAILS_BLOCKED_COUNTER);
            Response<String> emailsForwardedRs = pipeline.get(RedisKeys.EMAILS_FORWARDED_COUNTER);
            Response<String> proxiesActivatedRs = pipeline.get(RedisKeys.PROXIES_ACTIVATED_COUNTER);

            pipeline.sync();

            activeProxies = activeProxiesRs.get().intValue();
            emailsBlocked = toInt(emailsBlockedRs.get());
            emailsForwarded = toInt(emailsForwardedRs.get());
            proxiesActivated = toInt(proxiesActivatedRs.get());
        } catch (Exception ex) {
            LOGGER.error("Error while connecting to Redis", ex);
            returnJedisOnException(jedisPool, jedis, ex);
            return internalServerError();
        }
        jedisPool.returnResource(jedis);

        return ok(stats.render(lang(), activeProxies, emailsBlocked, emailsForwarded, proxiesActivated));
    }

    private int toInt(String str) {
        if (Strings.isNullOrEmpty(str)) {
            return 0;
        }
        return Integer.parseInt(str);
    }
}
