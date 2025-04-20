package com.xiongsu.backend.dm.logger;

import com.xiongsu.backend.utils.Panic;
import com.xiongsu.backend.utils.Parser;
import com.xiongsu.common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public interface Logger {
    void log(byte[] data);
    void truncate(long x) throws Exception;
    byte[] next();
    void rewind();
    void close();

    //用于首次创建日志文件。它会确保文件不存在，创建它，写入初始元数据（如校验和0），然后返回一个基于这个新文件初始化的 Logger。
    public static Logger create(String path) {
        File f = new File(path+LoggerImpl.LOG_SUFFIX);
        try {
            if (!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
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

        ByteBuffer buf = ByteBuffer.wrap(Parser.int2Byte(0));
        try {
            fc.position(0);
            fc.write(buf);
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }

        return new LoggerImpl(raf, fc, 0);
    }

    //用于打开已经存在的日志文件。它会确保文件存在，然后创建一个 Logger 实例，并调用其 init() 方法来读取、验证文件内容，并设置好内部状态以供后续读写
    public static Logger open(String path) {
        File f = new File(path+LoggerImpl.LOG_SUFFIX);
        if (!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
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

        LoggerImpl lg = new LoggerImpl(raf, fc);
        lg.init();

        return lg;
    }
}
