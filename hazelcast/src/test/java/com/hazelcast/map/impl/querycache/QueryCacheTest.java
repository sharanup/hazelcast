package com.hazelcast.map.impl.querycache;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.QueryCacheConfig;
import com.hazelcast.core.EntryAdapter;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.IFunction;
import com.hazelcast.core.IMap;
import com.hazelcast.map.QueryCache;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.mapreduce.helpers.Employee;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.SqlPredicate;
import com.hazelcast.query.TruePredicate;
import com.hazelcast.spi.properties.GroupProperty;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class QueryCacheTest extends AbstractQueryCacheTestSupport {

    @SuppressWarnings("unchecked")
    private static final Predicate<Integer, Employee> TRUE_PREDICATE = TruePredicate.INSTANCE;

    @Test
    @SuppressWarnings("ConstantConditions")
    public void testQueryCache_whenIncludeValue_enabled() throws Exception {
        boolean includeValue = true;
        testQueryCache(includeValue);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void testQueryCache_whenIncludeValue_disabled() throws Exception {
        boolean includeValue = false;
        testQueryCache(includeValue);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void testQueryCache_whenInitialPopulation_enabled() throws Exception {
        boolean enableInitialPopulation = true;
        int numberOfElementsToBePutToIMap = 1000;
        int expectedSizeOfQueryCache = numberOfElementsToBePutToIMap;

        testWithInitialPopulation(enableInitialPopulation, expectedSizeOfQueryCache, numberOfElementsToBePutToIMap);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void testQueryCache_whenInitialPopulation_disabled() throws Exception {
        boolean enableInitialPopulation = false;
        int numberOfElementsToBePutToIMap = 1000;
        int expectedSizeOfQueryCache = 0;

        testWithInitialPopulation(enableInitialPopulation, expectedSizeOfQueryCache, numberOfElementsToBePutToIMap);
    }

    @Test
    public void testQueryCache_withLocalListener() {
        IMap<Integer, Integer> map = getMap(instances[0], mapName);

        String cacheName = randomString();
        config = new Config().setProperty(GroupProperty.PARTITION_COUNT.getName(), "1");

        for (int i = 0; i < 30; i++) {
            map.put(i, i);
        }
        final AtomicInteger countAddEvent = new AtomicInteger();
        final AtomicInteger countRemoveEvent = new AtomicInteger();

        final QueryCache<Integer, Integer> queryCache = map.getQueryCache(cacheName, new EntryAdapter() {
            @Override
            public void entryAdded(EntryEvent event) {
                countAddEvent.incrementAndGet();
            }

            @Override
            public void entryRemoved(EntryEvent event) {
                countRemoveEvent.incrementAndGet();
            }
        }, new SqlPredicate("this > 20"), true);

        for (int i = 0; i < 30; i++) {
            map.remove(i);
        }

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(0, queryCache.size());
            }
        });
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals("Count of add events wrong!", 9, countAddEvent.get());
            }
        });
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals("Count of remove events wrong!", 9, countRemoveEvent.get());
            }
        });
    }

    @Test
    public void testQueryCacheCleared_afterCalling_IMap_evictAll() {
        String cacheName = randomString();
        QueryCache<Integer, Employee> queryCache = map.getQueryCache(cacheName, TRUE_PREDICATE, false);

        populateMap(map, 1000);

        IFunction evictAll = new IFunction() {
            @Override
            public Object apply(Object ignored) {
                map.evictAll();
                return null;
            }
        };

        assertQueryCacheSizeEventually(0, evictAll, queryCache);
    }

    @Test
    public void testQueryCacheCleared_afterCalling_IMap_clear() {
        String cacheName = randomString();
        final QueryCache<Integer, Employee> queryCache = map.getQueryCache(cacheName, TRUE_PREDICATE, false);

        populateMap(map, 1000);

        IFunction clear = new IFunction() {

            @Override
            public Object apply(Object ignored) {
                map.clear();
                return null;
            }
        };

        assertQueryCacheSizeEventually(0, clear, queryCache);
    }

    @Test
    public void test_getName() {
        String cacheName = "cache-name";
        QueryCache<Integer, Employee> queryCache = map.getQueryCache(cacheName, TRUE_PREDICATE, false);

        assertEquals(cacheName, queryCache.getName());
    }

    @Test
    public void testDestroy_emptiesQueryCache() {
        int entryCount = 1000;
        final CountDownLatch numberOfAddEvents = new CountDownLatch(entryCount);
        String cacheName = randomString();
        QueryCache<Integer, Employee> queryCache
                = map.getQueryCache(cacheName, new EntryAddedListener<Integer, Employee>() {
            @Override
            public void entryAdded(EntryEvent<Integer, Employee> event) {
                numberOfAddEvents.countDown();
            }
        }, TRUE_PREDICATE, false);

        populateMap(map, entryCount);

        assertOpenEventually(numberOfAddEvents);

        queryCache.destroy();

        assertEquals(0, queryCache.size());
    }

    private void testWithInitialPopulation(boolean enableInitialPopulation, int expectedSize, int numberOfElementsToPut) {
        String cacheName = randomString();
        getConfig(mapName, cacheName, enableInitialPopulation);

        populateMap(map, numberOfElementsToPut);
        QueryCache<Integer, Employee> queryCache = map.getQueryCache(cacheName, TRUE_PREDICATE, true);

        assertEquals(expectedSize, queryCache.size());
    }

    private void testQueryCache(boolean includeValue) {
        IMap<Integer, Integer> map = getMap(instances[0], mapName);

        String cacheName = randomString();

        for (int i = 0; i < 50; i++) {
            map.put(i, i);
        }
        Predicate<Integer, Integer> predicate = new SqlPredicate("this > 5 AND this < 100");
        QueryCache<Integer, Integer> cache = map.getQueryCache(cacheName, predicate, includeValue);

        for (int i = 50; i < 100; i++) {
            map.put(i, i);
        }

        int expected = 94;
        assertQueryCacheSizeEventually(expected, cache);
    }

    private Config getConfig(String mapName, String cacheName, boolean enableInitialPopulation) {
        QueryCacheConfig queryCacheConfig = new QueryCacheConfig(cacheName);
        queryCacheConfig
                .setPopulate(enableInitialPopulation)
                .getPredicateConfig().setImplementation(TruePredicate.INSTANCE);

        return addConfig(mapName, queryCacheConfig);
    }

    private Config addConfig(String mapName, QueryCacheConfig queryCacheConfig) {
        MapConfig mapConfig = new MapConfig(mapName);
        mapConfig.addQueryCacheConfig(queryCacheConfig);

        return config.addMapConfig(mapConfig);
    }

    private void assertQueryCacheSizeEventually(final int expected, final IFunction<?, ?> function, final QueryCache queryCache) {
        AssertTask task = new AssertTask() {
            @Override
            public void run() throws Exception {
                function.apply(null);
                assertEquals(expected, queryCache.size());
            }
        };

        assertTrueEventually(task);
    }

    private void assertQueryCacheSizeEventually(final int expected, final QueryCache cache) {
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(expected, cache.size());
            }
        });
    }
}
