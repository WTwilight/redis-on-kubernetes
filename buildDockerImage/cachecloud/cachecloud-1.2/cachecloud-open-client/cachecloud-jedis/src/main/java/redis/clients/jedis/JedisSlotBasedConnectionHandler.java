package redis.clients.jedis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import redis.clients.jedis.exceptions.JedisConnectionException;

public class JedisSlotBasedConnectionHandler extends JedisClusterConnectionHandler {

  public JedisSlotBasedConnectionHandler(Set<HostAndPort> nodes,
      final GenericObjectPoolConfig poolConfig, int timeout) {
    this(nodes, poolConfig, timeout, timeout);
  }

  public JedisSlotBasedConnectionHandler(Set<HostAndPort> nodes,
      final GenericObjectPoolConfig poolConfig, int connectionTimeout, int soTimeout) {
    super(nodes, poolConfig, connectionTimeout, soTimeout);
  }

  @Override
  public Jedis getConnection() {
    // In antirez's redis-rb-cluster implementation,
    // getRandomConnection always return valid connection (able to
    // ping-pong)
    // or exception if all connections are invalid

    List<JedisPool> pools = getShuffledNodesPool();

    for (JedisPool pool : pools) {
      Jedis jedis = null;
      try {
        jedis = pool.getResource();

        if (jedis == null) {
          continue;
        }

        String result = jedis.ping();

        if (result.equalsIgnoreCase("pong")) return jedis;

        pool.returnBrokenResource(jedis);
      } catch (JedisConnectionException ex) {
        if (jedis != null) {
          pool.returnBrokenResource(jedis);
        }
      }
    }

    throw new JedisConnectionException("no reachable node in cluster");
  }

  @Override
  public Jedis getConnectionFromSlot(int slot) {
    JedisPool connectionPool = cache.getSlotPool(slot);
    if (connectionPool != null) {
      // It can't guaranteed to get valid connection because of node
      // assignment
      return connectionPool.getResource();
    } else {
      return getConnection();
    }
  }
}
