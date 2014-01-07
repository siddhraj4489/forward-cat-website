package controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardcat.common.ProxyMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Controller;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

abstract class AbstractController extends Controller {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractController.class.getName());

    /**
     * Returns the {@link ProxyMail} linked to the given proxy key or null
     * if it does not exist
     */
    protected ProxyMail getProxy(String proxyKey, JedisPool jedisPool, ObjectMapper mapper) {
        ProxyMail proxy;
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();

            // Getting the proxy as a string
            String proxyString = jedis.get(proxyKey);
            if (proxyString == null) {
                LOGGER.debug("Proxy % doesn't exist", proxyString);
                return null;
            }

            // Checking that the hash is correct
            proxy = mapper.readValue(proxyString, ProxyMail.class);
        } catch (Exception ex) {
            LOGGER.error("Error while connecting to Redis", ex);
            returnJedisOnException(jedisPool, jedis, ex);
            return null;
        }
        jedisPool.returnResource(jedis);
        return proxy;
    }

    protected void returnJedisOnException(JedisPool pool, Jedis jedis, Exception ex) {
        if (ex instanceof JedisConnectionException) {
            pool.returnBrokenResource(jedis);
        } else {
            pool.returnResource(jedis);
        }
    }
}
