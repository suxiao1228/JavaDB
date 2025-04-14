package com.xiongsu.backend.tm;

import com.xiongsu.backend.utils.Panic;
import com.xiongsu.common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;



public interface TransactionManager {
    long begin();
    void commit(long xid);
    void abort(long xid);
    boolean isActive(long xid);
    boolean isCommited(long xid);
    boolean isAborted(long xid);
    void close();

    //create 用于从零开始，open 用于从持久化的状态恢复

    //这个方法用于数据库首次初始化或需要重置事务状态时。它确保事务状态文件 (.xid 文件) 不存在，然后创建它，
    // 检查权限，打开它，写入一个初始的、空的头部，最后返回一个管理这个文件的 TransactionManagerImpl 实例。
    public static TransactionManagerImpl create(String path) {
        File f = new File(path+TransactionManagerImpl.XID_SUFFIX);
        try {
            if (!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (IOException e) {
            Panic.panic(e);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }
        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        //写空文件夹
        ByteBuffer buf = ByteBuffer.wrap(new byte[TransactionManagerImpl.LEN_XID_HEADER_LENGTE]);
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        return new TransactionManagerImpl(raf, fc);
    }

    //这个方法用于在数据库启动时加载现有的事务状态。它确保事务状态文件存在，检查权限，以读写模式打开文件，
    // 然后返回一个管理这个文件的 TransactionManagerImpl 实例。它依赖于文件内已有的数据来恢复事务管理器的状态
    public static TransactionManagerImpl open(String path) {
        File f = new File(path+TransactionManagerImpl.XID_SUFFIX);
        if (!f.exists()) {
            Panic.panic(Error.FileExistsException);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }
        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }
        return new TransactionManagerImpl(raf, fc);
    }
}
