package com.xiongsu.backend.tm;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransactionManagerImpl implements TransactionManager{

    //XID文件头长度
    static final int LEN_XID_HEADER_LENGTE = 8;
    //每个事务的占用长度
    private static final int XID_FIELD_SIZE = 1;

    //事务的三种状态
    private static final byte FIELD_TRAN_ACTIVE = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED = 2;

    //超级事务，永远为commited状态
    public static final long SUPER_XID = 0;

    static final String XID_SUFFIX = ".xid";

    private RandomAccessFile file;
    private FileChannel fc;
    private long xidCounter;
    private Lock counterLock;

    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        counterLock = new ReentrantLock();
        checkXIDCounter();
    }

    /**
     * 检查XID文件是否合法
     * 读取XID_FILE_HEADER中的xidcounter，根据它计算文件的理论长度，对比实际长度
     */
    private void checkXIDCounter() {
        long fileLen = 0;

    }

    public long begin() {
        return 0;
    }

    public void commit(long xid) {

    }

    public void abort(long xid) {

    }

    public boolean isActive(long xid) {
        return false;
    }

    public boolean isCommited(long xid) {
        return false;
    }

    public boolean isAborted(long xid) {
        return false;
    }

    public void close() {

    }
}
