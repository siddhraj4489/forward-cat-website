package controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardcat.common.ProxyMail;
import com.forwardcat.common.RedisKeys;
import com.google.inject.Inject;
import org.apache.mailet.MailAddress;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.i18n.Lang;
import play.mvc.Http;
import play.mvc.Result;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import views.html.proxy_created;

import static com.forwardcat.common.RedisKeys.generateProxyKey;
import static models.ControllerUtils.*;
import static models.ExpirationUtils.*;
import static models.JedisHelper.returnJedisOnException;

public class ConfirmProxy extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmProxy.class.getName());
    private final JedisPool jedisPool;
    private final ObjectMapper mapper;

    @Inject
    ConfirmProxy(JedisPool jedisPool, ObjectMapper mapper) {
        this.jedisPool = jedisPool;
        this.mapper = mapper;
    }

    public Result confirm(String p, String h) throws Exception {
        Http.Request request = request();

        // Checking params
        MailAddress proxyMail = toMailAddress(p);
        if (proxyMail == null || h == null) {
            LOGGER.debug("Wrong params: {}", request);
            return badRequest();
        }

        // Getting the proxy
        String proxyKey = generateProxyKey(proxyMail);
        ProxyMail proxy = getProxy(proxyKey, jedisPool, mapper);
        if (proxy == null) {
            return badRequest();
        }

        // Checking that the hash is correct
        String hashValue = getHash(proxy);
        if (!h.equals(hashValue)) {
            LOGGER.debug("Hash values are not equals %s - %s", h, hashValue);
            return badRequest();
        }

        // Checking that the proxy is not already active
        if (proxy.isActive()) {
            LOGGER.debug("Proxy {} is already active", proxy);
            return badRequest();
        }
        proxy.activate();

        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            Pipeline pipeline = jedis.pipelined();

            // Calculating the TTL of the proxy
            DateTime expirationTime = toDateTime(proxy.getExpirationTime());
            DateTime alertTime = getAlertTime(expirationTime);

            pipeline.set(proxyKey, mapper.writeValueAsString(proxy)); // Saving the proxy
            pipeline.expire(proxyKey, secondsTo(expirationTime)); // Setting TTL
            pipeline.zadd(RedisKeys.ALERTS_SET, alertTime.getMillis(), proxyMail.toString()); // Adding an alert
            pipeline.incr(RedisKeys.PROXIES_ACTIVATED_COUNTER); // Incrementing proxies activated
            pipeline.sync();
        } catch (Exception ex) {
            LOGGER.error("Error while connecting to Redis", ex);
            returnJedisOnException(jedisPool, jedis, ex);
            return internalServerError();
        }
        jedisPool.returnResource(jedis);

        // Generating the response
        DateTime expirationTime = toDateTime(proxy.getExpirationTime());
        Lang language = getBestLanguage(request, lang());
        String date = formatInstant(expirationTime, language);
        return ok(proxy_created.render(language, proxyMail.toString(), date));
    }
}
