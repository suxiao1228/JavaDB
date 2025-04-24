package com.xiongsu.backend.dm.pageIndex;

import com.xiongsu.backend.dm.pageCache.PageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageIndex {
    //将一页划分为40个区间
    private static final int INTERVALS_NO = 40;
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;//区间长度 8192/40=204

    private Lock lock;
    private List<PageInfo>[] lists;

    @SuppressWarnings("unchecked")
    public PageIndex() {
        lock = new ReentrantLock();
        lists = new List[INTERVALS_NO+1];
        for (int i = 0; i < INTERVALS_NO+1; i++) {
            lists[i] = new ArrayList<>();
        }
    }

    //因为同一个页面是不允许并发写的，在上层模块使用完这个页面之后，需要重新将其插入到`PaegIndex`;

    /**
     * 根据给定的页面编号和空闲空间大小添加一个 PageInfo 对象。
     * @param pgno 页面编号
     * @param freeSpace 页面的空闲空间大小
     */
    public void add(int pgno, int freeSpace) {
        lock.lock();//获取锁，保证线程安全
        try {
            int number = freeSpace / THRESHOLD;//计算空闲空间大小对应的区间编号
            lists[number].add(new PageInfo(pgno, freeSpace));//在对应的区间列表中添加一个新的PageInfo对象
        } finally {
            lock.unlock();//释放锁
        }
    }

    /**
     * 根据空闲空间的大小计算所处的编号位置，从PageIndex中获取页面
     * @param spaceSize  需要的空间大小
     * @return  一个PageInfo对象，其空闲空间大于或等于给定的空间大小，如果没有找到合适的PageInfo,返回null
     */
    //根据空闲空间的大小计算所处的编号位置，从`PageIndex`中获取页面
    public PageInfo select(int spaceSize) {
        lock.lock();
        try {
            int number = spaceSize / THRESHOLD; // 计算需要的空间大小对应的区间编号
            //此处+1主要是为了向上取整
            /*
            1.假设需要存储的字节大小为5168，此时计算出来的区间号是25，但是25*204=5100显然是不满足条件的
            2.此时向上取整找到26，而26*204=5304，是满足插入条件的
             */
            if (number < INTERVALS_NO) number++;//如果计算出的区间编号小于总督区间数，编号+1
            while (number <= INTERVALS_NO) {//从计算出的区间编号开始，向上寻找合适的PageInfo
                if (lists[number].size() == 0) { // 如果当前区间没有PageInfo,继续查找下一个区间
                    number++;
                    continue;
                }
                return lists[number].remove(0);//如果当前区间有PageInfo,返回第一个PageInfo,并从列表中移除
            }
            return null;//如果没有找到合适的PageInfo,返回null
        } finally {
            lock.unlock();//释放锁
        }
    }
}
