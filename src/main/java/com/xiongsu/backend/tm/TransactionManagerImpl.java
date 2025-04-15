package com.xiongsu.backend.tm;

import com.xiongsu.backend.utils.Panic;
import com.xiongsu.backend.utils.Parser;
import com.xiongsu.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
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
        try {
            fileLen = file.length();
        } catch (IOException e) {
            Panic.panic(Error.BadXIDFileException);
        }
        if (fileLen < LEN_XID_HEADER_LENGTE) {
            Panic.panic(Error.BadXIDFileException);
        }

        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTE);
        try {
            fc.position(0);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.xidCounter = Parser.parseLong(buf.array());
        long end = getXidPosition(this.xidCounter + 1);
        if (end != fileLen) {
            Panic.panic(Error.BadXIDFileException);
        }
    }

    //根据事务xid取得其在xid文件中对应的位置
    private long getXidPosition(long xid) {
        return LEN_XID_HEADER_LENGTE + (xid-1)*XID_FIELD_SIZE;
    }

    // 开始一个事务，并返回XID
    public long begin() {
        // 锁定计数器，防止并发问题
        counterLock.lock();
        try {
            // xidCounter是当前事务的计数器，每开始一个新的事务，就将其加1
            long xid = xidCounter + 1;
            // 调用updateXID方法，将新的事务ID和事务状态（这里是活动状态）写入到XID文件中
            updateXID(xid, FIELD_TRAN_ACTIVE);
            // 调用incrXIDCounter方法，将事务计数器加1，并更新XID文件的头部信息
            incrXIDCounter();
            // 返回新的事务ID
            return xid;
        } finally {
            // 释放锁
            counterLock.unlock();
        }
    }

    // 更新xid事务的状态为status
    private void updateXID(long xid, byte status) {
        //获取事务xid在xid文件中对应的位置
        long offset = getXidPosition(xid);
        //创建一个长度为XID_FIELD_SIZE的字节数组
        byte[] tmp = new byte[XID_FIELD_SIZE];
        //将事务状态设置为status
        tmp[0] = status;
        //使用字节数组创建一个ByteBuffer
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try {
            //将文件通道的位置设置为offset
            fc.position(offset);
            //将ByteBuffer中的数据写入到文件通道
            fc.write(buf);
        } catch (IOException e) {
            //如果出现异常，调用Panic.panic方法处理
            Panic.panic(e);
        }
        try {
            //强制将文件通道中的所有未写入的数据写入到磁盘
            fc.force(false);
        } catch (IOException e) {
            //如果出现异常，调用Panic.panic方法处理
            Panic.panic(e);
        }
    }

    // 将XID加一，并更新XID Header
    private void incrXIDCounter() {
        // 事务总数加一
        xidCounter ++;
        // 将新的事务总数转换为字节数组，并用ByteBuffer包装
        ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));
        try {
            // 将文件通道的位置设置为0，即文件的开始位置
            fc.position(0);
            // 将ByteBuffer中的数据写入到文件通道，即更新了XID文件的头部信息
            fc.write(buf);
        } catch (IOException e) {
            // 如果出现异常，调用Panic.panic方法处理
            Panic.panic(e);
        }
        try {
            // 强制将文件通道中的所有未写入的数据写入到磁盘
            fc.force(false);
        } catch (IOException e) {
            // 如果出现异常，调用Panic.panic方法处理
            Panic.panic(e);
        }
    }

    //提交XID事务
    public void commit(long xid) {
        updateXID(xid, FIELD_TRAN_COMMITTED);
    }

    //回滚XID事务
    public void abort(long xid) {
        updateXID(xid, FIELD_TRAN_ABORTED);
    }

    public boolean isActive(long xid) {
        if (xid==SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    public boolean isCommited(long xid) {
        if (xid == SUPER_XID) return true;
        return checkXID(xid, FIELD_TRAN_COMMITTED);
    }

    public boolean isAborted(long xid) {
        if (xid==SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ABORTED);
    }

    // 检测XID事务是否处于status状态
    private boolean checkXID(long xid, byte status) {
        // 计算事务ID在XID文件中的位置
        long offset = getXidPosition(xid);
        // 创建一个新的字节缓冲区（ByteBuffer），长度为XID_FIELD_SIZE
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
        try {
            // 将文件通道的位置设置为offset
            fc.position(offset);
            // 从文件通道读取数据到字节缓冲区
            fc.read(buf);
        } catch (IOException e) {
            // 如果出现异常，调用Panic.panic方法处理
            Panic.panic(e);
        }
        // 检查字节缓冲区的第一个字节是否等于给定的状态
        // 如果等于，返回true，否则返回false
        return buf.array()[0] == status;
    }

    public void close() {
        try {
            fc.close();
            file.close();
        }catch (IOException e ){
            Panic.panic(e);
        }
    }
}
