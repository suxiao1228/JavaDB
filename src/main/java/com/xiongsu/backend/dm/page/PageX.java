package com.xiongsu.backend.dm.page;

import com.xiongsu.backend.dm.pageCache.PageCache;
import com.xiongsu.backend.utils.Parser;

import java.util.Arrays;

/**
 * PageX管理普通页
 * 普通页结构
 * [FreeSpaceOffset] [Date]
 * FreeSpaceOffset: 2字节 空闲位置开始偏移
 */
public class PageX {

    private static final short OF_FREE = 0;
    private static final short OF_DATA = 2;
    public static final int MAX_FREE_SPACE = PageCache.PAGE_SIZE - OF_DATA;

    public static byte[] initRaw() {
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setFSO(raw, OF_DATA);
        return raw;
    }

    private static void setFSO(byte[] raw, short ofDate) {
        System.arraycopy(Parser.short2Byte(ofDate), 0, raw, OF_FREE, OF_DATA);
    }

    //获取pg的FSO
    public static short getFSO(Page pg) {
        return getFSO(pg.getData());
    }

    private static short getFSO(byte[] raw) {
        return Parser.parseShort(Arrays.copyOfRange(raw, 0, 2));
    }

    //将raw插入pg中， 返回插入位置
    public static short insert(Page pg, byte[] raw) {
        pg.setDirty(true);// 将pg的dirty标志设置为true，表示pg的数据已经被修改
        short offset = getFSO(pg.getData());// 获取pg的空闲空间偏移量
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);// 将raw的数据复制到pg的数据中的offset位置
        setFSO(pg.getData(), (short)(offset + raw.length));// 更新pg的空闲空间偏移量
        return offset;// 返回插入位置
    }

    //获取页面的空闲时间大小
    public static int getFreeSpace(Page pg) {
        return PageCache.PAGE_SIZE - (int)getFSO(pg.getData());
    }

    //将raw插入pg中的offset位置，并将pg的offset设置为较大的offset
    public static void recoverInsert(Page pg, byte[] raw, short offset) {
        pg.setDirty(true);
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);

        short rawFSO = getFSO(pg.getData());
        if (rawFSO < offset + raw.length) {
            setFSO(pg.getData(), (short)(offset+raw.length));
        }
    }

    //将raw插入到pg中的offset位置，不更新update
    public static void recoverUpdate(Page pg, byte[] raw, short offset) {
        pg.setDirty(true);
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
    }
}
