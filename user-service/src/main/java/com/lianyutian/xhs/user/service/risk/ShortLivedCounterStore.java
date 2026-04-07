package com.lianyutian.xhs.user.service.risk;

import java.time.Duration;
import java.util.Collection;

public interface ShortLivedCounterStore {

    long get(String key);

    long increment(String key, Duration ttl);

    void delete(Collection<String> keys);
}
