package com.xiongsu.backend.utils;

//当程序某个地方捕获到一个它认为非常严重、无法继续安全运行下去的异常时，就会调用 panic 方法。
public class Panic {
    public static void panic(Exception err) {
        err.printStackTrace();//将异常的堆栈跟踪信息打印到控制台
        System.exit(1);//立即终止当前正在运行的 Java 虚拟机（JVM）  参数为0，正常退出   参数为1，异常退出
    }
}
