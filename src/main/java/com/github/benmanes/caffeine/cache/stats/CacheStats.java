/*
 * Copyright 2014 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.cache.stats;

import java.util.Objects;
import java.util.concurrent.Callable;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * Statistics about the performance of a {@link Cache}.
 * <p>
 * Cache statistics are incremented according to the following rules:
 * <ul>
 *   <li>When a cache lookup encounters an existing cache entry {@code hitCount} is incremented.
 *   <li>When a cache lookup first encounters a missing cache entry, a new entry is loaded.
 *   <ul>
 *     <li>After successfully loading an entry {@code missCount} and {@code loadSuccessCount} are
 *         incremented, and the total loading time, in nanoseconds, is added to
 *         {@code totalLoadTime}.
 *     <li>When an exception is thrown while loading an entry, {@code missCount} and {@code
 *         loadFailureCount} are incremented, and the total loading time, in nanoseconds, is
 *         added to {@code totalLoadTime}.
 *     <li>Cache lookups that encounter a missing cache entry that is still loading will wait
 *         for loading to complete (whether successful or not) and then increment {@code missCount}.
 *   </ul>
 *   <li>When an entry is computed through the {@linkplain Cache#asMap asMap}.
 *   <li>When an entry is evicted from the cache, {@code evictionCount} is incremented.
 *   <li>No stats are modified when a cache entry is invalidated or manually removed.
 *   <li>No stats are modified on a query to {@link Cache#getIfPresent}.
 *   <li>No stats are modified by non-computing operations invoked on the
 *       {@linkplain Cache#asMap asMap} view of the cache.
 * </ul>
 * <p>
 * A lookup is specifically defined as an invocation of one of the methods
 * {@link LoadingCache#get(Object)}, {@link LoadingCache#getUnchecked(Object)},
 * {@link Cache#get(Object, Callable)}, or {@link LoadingCache#getAll(Iterable)}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Immutable
public class CacheStats {
  private final long hitCount;
  private final long missCount;
  private final long loadSuccessCount;
  private final long loadFailureCount;
  private final long totalLoadTime;
  private final long evictionCount;

  /**
   * Constructs a new {@code CacheStats} instance.
   */
  public CacheStats(@Nonnegative long hitCount, @Nonnegative long missCount,
      @Nonnegative long loadSuccessCount, @Nonnegative long loadFailureCount,
      @Nonnegative long totalLoadTime, @Nonnegative long evictionCount) {
    if ((hitCount < 0) || (missCount < 0) || (loadSuccessCount < 0) || (loadFailureCount < 0)
        || (totalLoadTime < 0) || (evictionCount < 0)) {
      throw new IllegalArgumentException();
    }
    this.hitCount = hitCount;
    this.missCount = missCount;
    this.loadSuccessCount = loadSuccessCount;
    this.loadFailureCount = loadFailureCount;
    this.totalLoadTime = totalLoadTime;
    this.evictionCount = evictionCount;
  }

  /**
   * Returns the number of times {@link Cache} lookup methods have returned either a cached or
   * uncached value. This is defined as {@code hitCount + missCount}.
   */
  @Nonnegative
  public long requestCount() {
    return hitCount + missCount;
  }

  /**
   * Returns the number of times {@link Cache} lookup methods have returned a cached value.
   */
  @Nonnegative
  public long hitCount() {
    return hitCount;
  }

  /**
   * Returns the ratio of cache requests which were hits. This is defined as
   * {@code hitCount / requestCount}, or {@code 1.0} when {@code requestCount == 0}. Note that
   * {@code hitRate + missRate =~ 1.0}.
   */
  @Nonnegative
  public double hitRate() {
    long requestCount = requestCount();
    return (requestCount == 0) ? 1.0 : (double) hitCount / requestCount;
  }

  /**
   * Returns the number of times {@link Cache} lookup methods have returned an uncached (newly
   * loaded) value, or null. Multiple concurrent calls to {@link Cache} lookup methods on an absent
   * value can result in multiple misses, all returning the results of a single cache load
   * operation.
   */
  @Nonnegative
  public long missCount() {
    return missCount;
  }

  /**
   * Returns the ratio of cache requests which were misses. This is defined as
   * {@code missCount / requestCount}, or {@code 0.0} when {@code requestCount == 0}.
   * Note that {@code hitRate + missRate =~ 1.0}. Cache misses include all requests which
   * weren't cache hits, including requests which resulted in either successful or failed loading
   * attempts, and requests which waited for other threads to finish loading. It is thus the case
   * that {@code missCount &gt;= loadSuccessCount + loadFailureCount}. Multiple
   * concurrent misses for the same key will result in a single load operation.
   */
  @Nonnegative
  public double missRate() {
    long requestCount = requestCount();
    return (requestCount == 0) ? 0.0 : (double) missCount / requestCount;
  }

  /**
   * Returns the total number of times that {@link Cache} lookup methods attempted to load new
   * values. This includes both successful load operations, as well as those that threw exceptions.
   * This is defined as {@code loadSuccessCount + loadFailureCount}.
   */
  @Nonnegative
  public long loadCount() {
    return loadSuccessCount + loadFailureCount;
  }

  /**
   * Returns the number of times {@link Cache} lookup methods have successfully loaded a new value.
   * This is always incremented in conjunction with {@link #missCount}, though {@code missCount}
   * is also incremented when an exception is encountered during cache loading (see
   * {@link #loadFailureCount}). Multiple concurrent misses for the same key will result in a
   * single load operation.
   */
  @Nonnegative
  public long loadSuccessCount() {
    return loadSuccessCount;
  }

  /**
   * Returns the number of times {@link Cache} lookup methods failed to load a new value, either
   * because no value was found or an exception was thrown while loading. This is always incremented
   * in conjunction with {@code missCount}, though {@code missCount} is also incremented when cache
   * loading completes successfully (see {@link #loadSuccessCount}). Multiple concurrent misses for
   * the same key will result in a single load operation.
   */
  @Nonnegative
  public long loadFailureCount() {
    return loadFailureCount;
  }

  /**
   * Returns the ratio of cache loading attempts which threw exceptions. This is defined as
   * {@code loadFailureCount / (loadSuccessCount + loadFailureCount)}, or {@code 0.0} when
   * {@code loadSuccessCount + loadFailureCount == 0}.
   */
  @Nonnegative
  public double loadExceptionRate() {
    long totalLoadCount = loadSuccessCount + loadFailureCount;
    return (totalLoadCount == 0)
        ? 0.0
        : (double) loadFailureCount / totalLoadCount;
  }

  /**
   * Returns the total number of nanoseconds the cache has spent loading new values. This can be
   * used to calculate the miss penalty. This value is increased every time {@code loadSuccessCount}
   * or {@code loadFailureCount} is incremented.
   */
  @Nonnegative
  public long totalLoadTime() {
    return totalLoadTime;
  }

  /**
   * Returns the average time spent loading new values. This is defined as
   * {@code totalLoadTime / (loadSuccessCount + loadFailureCount)}.
   */
  @Nonnegative
  public double averageLoadPenalty() {
    long totalLoadCount = loadSuccessCount + loadFailureCount;
    return (totalLoadCount == 0)
        ? 0.0
        : (double) totalLoadTime / totalLoadCount;
  }

  /**
   * Returns the number of times an entry has been evicted. This count does not include manual
   * {@linkplain Cache#invalidate invalidations}.
   */
  @Nonnegative
  public long evictionCount() {
    return evictionCount;
  }

  /**
   * Returns a new {@code CacheStats} representing the difference between this {@code CacheStats}
   * and {@code other}. Negative values, which aren't supported by {@code CacheStats} will be
   * rounded up to zero.
   */
  @Nonnull
  public CacheStats minus(@Nonnull CacheStats other) {
    return new CacheStats(
        Math.max(0, hitCount - other.hitCount),
        Math.max(0, missCount - other.missCount),
        Math.max(0, loadSuccessCount - other.loadSuccessCount),
        Math.max(0, loadFailureCount - other.loadFailureCount),
        Math.max(0, totalLoadTime - other.totalLoadTime),
        Math.max(0, evictionCount - other.evictionCount));
  }

  /**
   * Returns a new {@code CacheStats} representing the sum of this {@code CacheStats} and
   * {@code other}.
   */
  @Nonnull
  public CacheStats plus(@Nonnull CacheStats other) {
    return new CacheStats(
        hitCount + other.hitCount,
        missCount + other.missCount,
        loadSuccessCount + other.loadSuccessCount,
        loadFailureCount + other.loadFailureCount,
        totalLoadTime + other.totalLoadTime,
        evictionCount + other.evictionCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hitCount, missCount, loadSuccessCount,
        loadFailureCount, totalLoadTime, evictionCount);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    } else if (!(o instanceof CacheStats)) {
      return false;
    }
    CacheStats other = (CacheStats) o;
    return hitCount == other.hitCount
        && missCount == other.missCount
        && loadSuccessCount == other.loadSuccessCount
        && loadFailureCount == other.loadFailureCount
        && totalLoadTime == other.totalLoadTime
        && evictionCount == other.evictionCount;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{'
        + "hitCount=" + hitCount + ','
        + "missCount=" + missCount + ','
        + "loadSuccessCount" + loadSuccessCount + ','
        + "loadFailureCount" + loadFailureCount + ','
        + "totalLoadTime" + totalLoadTime + ','
        + "evictionCount" + evictionCount + ',';
  }
}
