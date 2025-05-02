package com.xiongsu.backend.vm;


import com.google.common.primitives.Bytes;
import com.xiongsu.backend.common.SubArray;
import com.xiongsu.backend.dm.dataltem.DataItem;
import com.xiongsu.backend.dm.dataltem.DataItemImpl;
import com.xiongsu.backend.utils.Parser;

import java.util.Arrays;

/**
 * VM向上层抽象出entry
 * entry结构：
 * [XMIN] [XMAX] [data]
 */
//XMIN:创建该条记录（版本）的事务编号
//XMAX:删除该条记录（版本）的事务编号
//DATA:是这条记录持有的数据
public class Entry {
    private static final int OF_XMIN = 0;//定义了XMIN的偏移量为0
    private static final int OF_XMAX = OF_XMIN+8;// 定义了XMAX的偏移量为XMIN偏移量后的8个字节
    private static final int OF_DATA = OF_XMAX+8;// 定义了DATA的偏移量为XMAX偏移量后的8个字节

    private long uid;//uid字段，用来唯一标识一个Entry的
    private DataItem dataItem;//DataItem对象，用来存储数据
    private VersionManager vm;// VersionManager对象，用来管理版本的

    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        if (dataItem == null) {
            return null;
        }
        Entry entry = new Entry();
        entry.uid = uid;
        entry.dataItem = dataItem;
        entry.vm = vm;
        return entry;
    }

    //静态方法，用来加载一个Entry.它首先从VersionManager中读取数据，然后创建一个新的Entry
    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem di = ((VersionManagerImpl)vm).dm.read(uid);
        return newEntry(vm, di, uid);
    }

    /**
     * 生成日志格式数据
     * @param xid
     * @param data
     * @return
     */
    public static byte[] wrapEntryRaw(long xid, byte[] data) {
        byte[] xmin = Parser.long2Byte(xid);// 将事务id转为8字节数组
        byte[] xmax = new byte[8];// 创建一个空的8字节数组，等待版本修改或删除是才修改
        return Bytes.concat(xmin, xmax, data);// 拼接成日志格式
    }

    public void release() {
        ((VersionManagerImpl)vm).releaseEntry(this);
    }

    //用来移除一个Entry, 它通过调用dataItem的release方法来实现
    public void remove() {
        dataItem.release();
    }

    /**
     * 获取记录中持有的数据，也就需要按照上面这个结构来解析
     * @return
     */
    //以拷贝的形式返回内容
    public byte[] data() {
        dataItem.rLock();// 加锁，确保数据安全
        try {
            SubArray sa = dataItem.data();// 获取日志数据
            byte[] data = new byte[sa.end - sa.start - OF_DATA];// 创建一个去除前16字节的数组，因为前16字节表示 xmin and xmax
            System.arraycopy(sa.raw, sa.start+OF_DATA, data, 0, data.length);// 拷贝数据到data数组上
            return data;
        } finally {
            dataItem.rUnLock();//释放锁
        }
    }

    public long getXmin() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMIN, sa.start+OF_XMAX));
        } finally {
            dataItem.rUnLock();
        }
    }

    public long getXmax() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMAX, sa.start+OF_DATA));
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 当需要对数据进行修改时，就需要设置xmax的值
     * @param xid
     */
    public void setXmax(long xid) {
        dataItem.before();// 在修改或删除之前先拷贝好旧数值
        try {
            SubArray sa = dataItem.data();// 获取需要删除的日志数据
            System.arraycopy(Parser.long2Byte(xid), 0, sa.raw, sa.start+OF_XMAX, 8);// 将事务编号拷贝到 8~15 处字节
        } finally {
            dataItem.after(xid);// 生成一个修改日志
        }
    }

    public long getUid() {
        return uid;
    }
}
