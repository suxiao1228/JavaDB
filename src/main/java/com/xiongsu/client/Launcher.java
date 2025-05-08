package com.xiongsu.client;

import com.xiongsu.transport.Encoder;
import com.xiongsu.transport.Packager;
import com.xiongsu.transport.Transporter;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

//启动客户端并连接服务器；
public class Launcher {
    public static void main(String[] args) throws UnknownHostException, IOException {
        Socket socket = new Socket("127.0.0.1", 9999);// 定义服务器监听的端口号
        Encoder e = new Encoder();
        Transporter t = new Transporter(socket);
        Packager packager = new Packager(t, e);

        Client client = new Client(packager);
        Shell shell = new Shell(client);
        shell.run();
    }
}
