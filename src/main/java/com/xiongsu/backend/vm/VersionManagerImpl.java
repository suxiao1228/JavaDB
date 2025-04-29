package com.xiongsu.backend.vm;

import com.xiongsu.backend.common.AbstractCache;
import com.xiongsu.backend.dm.DataManager;
import com.xiongsu.backend.tm.TransactionManager;

import java.util.Map;
import java.util.concurrent.locks.Lock;

public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager {

    TransactionManager tm;
    DataManager dm;
    Map<Long, Transaction> activeTransaction;
    Lock lock;
    LocKTable lt;

}
