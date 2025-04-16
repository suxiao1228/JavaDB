package com.xiongsu.backend.common;

import com.xiongsu.common.Error;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AbstractCache 实现了一个引用计数策略的缓存
 */
public abstract class AbstractCache<T> {
    private HashMap<Long, T> cache;            //实际缓存的数据
    private HashMap<Long, Integer> references;  //元素的引用个数
    private HashMap<Long, Boolean> getting;    //正在获取某资源的线程

    private int maxResource;                   //缓存的最大缓存资源数
    private int count = 0;                     //缓存中元素的个数
    private Lock lock;

    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;
        cache = new HashMap<>();
        references = new HashMap<>();
        getting = new HashMap<>();
        lock = new ReentrantLock();
    }

    //从缓存中获取资源
    protected T get(long key) throws Exception {
        //循环直到获取资源
        while (true) {
            //获取锁
            lock.lock();
            if (getting.containsKey(key)) {
                // 请求的资源正在被其他的资源获取
                lock.unlock();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                continue;
            }
            if (cache.containsKey(key)) {
                //资源在缓存中，直接返回，并增加引用计数
                T obj = cache.get(key);
                references.put(key, references.get(key)+1);
                lock.unlock();
                return obj;
            }

            //如果资源不在缓存中，尝试获取该资源。如果缓存已满，抛出异常
            if (maxResource > 0 && count == maxResource) {
                lock.unlock();
                throw Error.CacheFullException;
            }
            count ++;
            getting.put(key, true);
            lock.unlock();
            break;
        }
        //尝试获取资源
        T obj = null;
        try {
            obj = getForCache(key);
        } catch (Exception e) {
            lock.lock();
            count --;
            getting.remove(key);
            lock.unlock();
            throw e;
        }

        //将获取到的资源添加到缓存中，并设置引用计数+1
        lock.lock();
        getting.remove(key);
        cache.put(key, obj);
        references.put(key, 1);
        lock.unlock();

        return obj;
    }

    /**
     * 强行释放一个缓存
     */
    protected void release(long key) {
        //获取锁
        lock.lock();
        try {
            //获取资源的引用计数并-1
            int ref = references.get(key)-1;
            if (ref == 0) { // 若引用计数为0
                T obj = cache.get(key);//从缓存中获取资源
                releaseForCache(obj);//处理资源的释放
                references.remove(key);//从引用计数的映射中移除资源
                cache.remove(key);//从缓存中移除资源
                count --;//将缓存中的资源计数-1
            } else {//如果引用计数不为0
                references.put(key, ref);//更新资源的引用计数
            }
        } finally {
            lock.unlock();//释放锁
        }
    }

    /**
     * 关闭缓存，写回所有资源
     */
    protected void close() {
        lock.lock();
        try {
            //获取所有资源key
            Set<Long> keys = cache.keySet();
            for (long key : keys) {
                //获取缓存
                T obj = cache.get(key);
                //释放缓存
                releaseForCache(obj);
                //引用计数移除缓存
                references.remove(key);
                //实际缓存移除缓存
                cache.remove(key);
            }
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    /**
     * 当资源不在缓存时的获取行为
     */
    protected abstract T getForCache(long key) throws Exception;

    /**
     * 当资源被驱逐时的写回行为
     */
    protected abstract void releaseForCache(T obj);
}
