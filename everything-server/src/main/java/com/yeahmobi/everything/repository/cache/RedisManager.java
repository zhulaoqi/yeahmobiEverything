package com.yeahmobi.everything.repository.cache;

import com.yeahmobi.everything.common.Config;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages Redis connections using Jedis connection pool.
 * Reads configuration from {@link Config} (redis.host, redis.port, redis.password, redis.database).
 */
public class RedisManager {

    private static final Logger LOGGER = Logger.getLogger(RedisManager.class.getName());

    private final JedisPool jedisPool;

    /**
     * Creates a RedisManager using application configuration.
     */
    public RedisManager() {
        this(Config.getInstance());
    }

    /**
     * Creates a RedisManager using the given Config instance.
     *
     * @param config the configuration to read Redis connection parameters from
     */
    public RedisManager(Config config) {
        this(
            config.getRedisHost(),
            config.getRedisPort(),
            config.getRedisPassword(),
            config.getRedisDatabase()
        );
    }

    /**
     * Creates a RedisManager with explicit connection parameters.
     * Useful for testing or custom configurations.
     *
     * @param host     Redis host
     * @param port     Redis port
     * @param password Redis password (null or empty for no auth)
     * @param database Redis database index
     */
    public RedisManager(String host, int port, String password, int database) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        String effectivePassword = (password != null && !password.isBlank()) ? password : null;

        this.jedisPool = new JedisPool(poolConfig, host, port, 2000, effectivePassword, database);
        LOGGER.info("RedisManager initialized: " + host + ":" + port + "/" + database);
    }

    /**
     * Creates a RedisManager with an externally provided JedisPool.
     * Primarily for testing with mock pools.
     *
     * @param jedisPool the JedisPool to use
     */
    public RedisManager(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Gets a Jedis instance from the connection pool.
     * The caller is responsible for closing the returned Jedis instance
     * (preferably using try-with-resources).
     *
     * @return a Jedis instance
     */
    public Jedis getJedis() {
        return jedisPool.getResource();
    }

    /**
     * Checks if the Redis connection is available.
     *
     * @return true if Redis is reachable, false otherwise
     */
    public boolean isAvailable() {
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis is not available: " + e.getMessage());
            return false;
        }
    }

    /**
     * Closes the connection pool and releases all resources.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            LOGGER.info("RedisManager closed.");
        }
    }
}
