package com.xiongsu.transport;

public class Package {
    byte[] data; // 存放数据信息
    Exception err; // 存放错误提示信息

    public Package(byte[] data, Exception err) {
        this.data = data;
        this.err = err;
    }

    public byte[] getData() {
        return data;
    }

    public Exception getErr() {
        return err;
    }
}
