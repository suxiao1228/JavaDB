package com.xiongsu.backend.tbm;


import com.xiongsu.backend.utils.Panic;
import com.xiongsu.common.Error;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

// 记录第一个表的uid
public class Booter {
    public static final String BOOTER_SUFFIX = ".bt";// 数据库启动信息文件的后缀
    public static final String BOOTER_TMP_SUFFIX = ".bt_tmp";// 数据库启动信息文件的临时后缀

    String path;// 数据库启动信息文件的路径
    File file;// 数据库启动信息文件

    /**
     * 创建一个新的Booter对象
     * @param path
     * @return
     */
    public static Booter create(String path) {
        removeBadTmp(path);// 删除可能存在的临时文件
        File f = new File(path+BOOTER_SUFFIX);// 创建一个新的文件对象，文件名是路径加上启动信息文件的后缀
        try {
            if (!f.createNewFile()) {// 尝试创建新的文件，如果文件已存在，则抛出异常
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);// 如果创建文件过程中出现异常，则处理异常
        }
        if (!f.canRead() || !f.canWrite()) {// 检查文件是否可读写，如果不可读写，则抛出异常
            Panic.panic(Error.FileCannotRWException);
        }
        return new Booter(path, f);// 返回新创建的Booter对象
    }

    /**
     * 打开一个已经存在的Booter对象
     * @param path
     * @return
     */
    public static Booter open(String path) {
        removeBadTmp(path);// 删除可能存在的临时文件
        File f = new File(path+BOOTER_SUFFIX);// 创建一个新的文件对象，文件名是路径加上启动信息文件的后缀
        if (!f.exists()) {// 如果文件不存在，则抛出异常
            Panic.panic(Error.FileNotExistsException);
        }
        if (!f.canRead() || !f.canWrite()) { // 检查文件是否可读写，如果不可读写，则抛出异常
            Panic.panic(Error.FileCannotRWException);
        }
        return new Booter(path, f);// 返回打开的Booter对象
    }

    /**
     * 删除可能存在的临时文件
     * @param path
     */
    private static void removeBadTmp(String path) {
        new File(path+BOOTER_SUFFIX).delete();// 删除路径加上临时文件后缀的文件
    }

    private Booter(String path, File file) {
        this.path = path;
        this.file = file;
    }

    public byte[] load() {
        byte[] buf = null;
        try {
            buf = Files.readAllBytes(file.toPath());// 读取文件的所有字节
        } catch (IOException e) {
            Panic.panic(e);
        }
        return buf;
    }

    /**
     * 更新启动信息文件的内容
     * @param data  要写入文件的数据
     */
    public void update(byte[] data) {
        File tmp = new File(path+BOOTER_SUFFIX);// 创建一个新的临时文件
        try {
            tmp.createNewFile(); // 尝试创建新的临时文件
        } catch (Exception e) {
            Panic.panic(e);// 如果创建文件过程中出现异常，则处理异常
        }
        if (!tmp.canRead() || !tmp.canWrite()) {// 检查临时文件是否可读写，如果不可读写，则抛出异常
            Panic.panic(Error.FileCannotRWException);
        }
        try (FileOutputStream out = new FileOutputStream(tmp)){
            out.write(data);// 将数据写入临时文件
            out.flush();// 刷新输出流，确保数据被写入文件
        } catch (IOException e) {
            Panic.panic(e);// 如果写入文件过程中出现异常，则处理异常
        }
        try {
            // 将临时文件移动到启动信息文件的位置，替换原来的文件
            Files.move(tmp.toPath(), new File(path+BOOTER_SUFFIX).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Panic.panic(e);// 如果移动文件过程中出现异常，则处理异常
        }
        file = new File(path+BOOTER_SUFFIX);// 更新file字段为新的启动信息文件
        if (!file.canRead() || !file.canWrite()) {// 检查新的启动信息文件是否可读写，如果不可读写，则抛出异常
            Panic.panic(Error.FileCannotRWException);
        }
    }
}
