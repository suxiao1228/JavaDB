package com.xiongsu.backend.dm.page;


import com.xiongsu.backend.dm.pageCache.PageCache;
import com.xiongsu.backend.utils.RandomUtil;

import java.util.Arrays;

/**
 * 特殊管理第一页
 * ValidCheck
 * db启动时给100~107字节处填入一个随机字节，db关闭时将其拷贝到108~115字节
 * 用来判断上一次数据库是否正常关闭
 */
public class PageOne {
    private static final int OF_VC = 100;
    private static final int LEN_VC = 8;

    public static byte[] InitRaw() {
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setVcOpen(raw);
        return raw;
    }

    //数据库启动时，调用
    //这个方法会生成 LEN_VC (8) 个随机字节，将这 8 个随机字节写入到第一页的 "Open Slot" (字节 100-107)。
    public static void setVcOpen(Page pg) {
        pg.setDirty(true);
        setVcOpen(pg.getData());
    }

    private static void setVcOpen(byte[] raw) {
        System.arraycopy(RandomUtil.randomBytes(LEN_VC), 0, raw, OF_VC, LEN_VC);
    }

    //当数据库准备正常关闭时，会调用 setVcClose 方法。
    //这个方法比较 "Open Slot" (100-107) 和 "Close Slot" (108-115) 这两个区域的字节内容。

    //如果两者内容完全相同： 这意味着上一次关闭时 setVcClose 成功执行并且其修改已持久化到磁盘。因此，数据库上次是正常关闭的。checkVc 返回 true。
    //如果两者内容不相同： 这意味着上一次关闭时，setVcClose 没有被执行，或者执行了但对应的页面修改没有成功写回磁盘（比如发生了崩溃）。因此，数据库上次是异常关闭的。checkVc 返回 false。
    public static void setVcClose(Page pg) {
        pg.setDirty(true);
        setVcClose(pg.getData());
    }

    private static void setVcClose(byte[] raw) {
        System.arraycopy(raw, OF_VC, raw, OF_VC+LEN_VC, LEN_VC);
    }

    public static boolean checkVc(Page pg) {
        return checkVc(pg.getData());
    }

    private static boolean checkVc(byte[] raw) {
        return Arrays.equals(Arrays.copyOfRange(raw, OF_VC, OF_VC+LEN_VC), Arrays.copyOfRange(raw, OF_VC+LEN_VC, OF_VC+2*LEN_VC));
    }
}
