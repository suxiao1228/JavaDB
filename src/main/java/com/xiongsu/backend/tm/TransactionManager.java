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
    long begin();// 开启一个新事务
    void commit(long xid);// 提交一个事务
    void abort(long xid);// 取消一个事务
    boolean isActive(long xid);// 查询一个事务的状态是否是正在进行的状态
    boolean isCommited(long xid);// 查询一个事务的状态是否是已提交
    boolean isAborted(long xid);// 查询一个事务的状态是否是已取消
    void close();// 关闭TM

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

    //启动时（通过open方法），构造函数中的 checkXIDCounter 方法会进行一次完整性校验。它读取文件头中的事务总数，并根据这个数量计算出文件的理论长度，然后与文件的实际长度进行比较。
    // 如果不一致，说明 .xid 文件已损坏，系统会通过 Panic 机制快速失败。
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
