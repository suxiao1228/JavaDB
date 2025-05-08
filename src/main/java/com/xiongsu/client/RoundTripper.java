package com.xiongsu.client;

import com.xiongsu.transport.Package;
import com.xiongsu.transport.Packager;

//用于发送请求并接受响应
public class RoundTripper {
    private Packager packager;

    public RoundTripper(Packager packager) {
        this.packager = packager;
    }

    public Package roundTrip(Package pkg) throws Exception {// 定义一个方法，用于处理请求的往返传输
        packager.send(pkg);// 发送请求包
        return packager.receive(); // 接收响应包，并返回
    }

    public void close() throws Exception {
        packager.close();
    }
}
