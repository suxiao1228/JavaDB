package com.xiongsu.backend.server;

import com.xiongsu.backend.tbm.TableManager;
import com.xiongsu.transport.Encoder;
import com.xiongsu.transport.Package;
import com.xiongsu.transport.Packager;
import com.xiongsu.transport.Transporter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

//`**Server**`是一个服务器类，主要作用是监听指定的端口号，接受客户端的连接请求，并为每个连接请求创建一个新的线程来处理；
public class Server {
    private int port;
    TableManager tbm;

    public Server(int port, TableManager tbm) {
        this.port = port;
        this.tbm = tbm;
    }

    public void start() {
        ServerSocket ss = null;// 创建一个ServerSocket对象，用于监听指定的端口
        try {
            ss = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Server listen to port: " + port);
        // 创建一个线程池，用于管理处理客户端连接请求的线程
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(10, 20, 1L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            while(true) {// 无限循环，等待并处理客户端的连接请求
                Socket socket = ss.accept(); // 接收一个客户端的连接请求
                Runnable worker = new HandleSocket(socket, tbm);// 创建一个新的HandleSocket对象，用于处理这个连接请求
                tpe.execute(worker);// 将这个HandleSocket对象提交给线程池，由线程池中的一个线程来执行
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {// 在最后，无论是否发生异常，都要关闭ServerSocket
            try {
                ss.close();
            } catch (IOException ignored) {}
        }
    }
}

//HandleSocket 类实现了 `**Runnable**`** **接口，在建立连接后初始化 `**Packager**`，随后就循环接收来自客户端的数据并处理；
// 主要通过 `Executor**` **对象来执行 `**SQL**`语句，在接受、执行SQL语句的过程中发生异常的话，将会结束循环，并关闭 `**Executor**`** **和 `**Package**`;
class HandleSocket implements Runnable {
    private Socket socket;
    private TableManager tbm;

    public HandleSocket(Socket socket, TableManager tbm) {
        this.socket = socket;
        this.tbm = tbm;
    }

    @Override
    public void run() {
        InetSocketAddress address = (InetSocketAddress)socket.getRemoteSocketAddress();//获取远程客户端的地址信息
        System.out.println("Establish connection: " + address.getAddress().getHostAddress()+":"+address.getPort());//打印客户端的IP地址和端口号
        Packager packager = null;
        try {
            Transporter t = new Transporter(socket);//创建一个Transporter对象，用于处理网络传输
            Encoder e = new Encoder();//创建一个Encode对象，用于处理数据的编码和解码
            packager = new Packager(t, e);//创建一个Packager对象，用于处理数据的打包和解包
        } catch(IOException e) {
            e.printStackTrace();// 如果在创建Transporter或Encoder时发生异常，打印异常信息并关闭socket
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return;
        }
        Executor exe = new Executor(tbm);// 创建一个Executor对象，用于执行SQL语句
        while(true) {
            Package pkg = null;
            try {
                pkg = packager.receive();// 从客户端接收数据包
            } catch(Exception e) {
                break;// 如果在接收数据包时发生异常，结束循环
            }
            byte[] sql = pkg.getData();// 获取数据包中的SQL语句
            byte[] result = null;
            Exception e = null;
            try {
                result = exe.execute(sql);// 执行SQL语句，并获取结果
            } catch (Exception e1) {
                e = e1;// 如果在执行SQL语句时发生异常，保存异常信息
                e.printStackTrace();
            }
            pkg = new Package(result, e);// 创建一个新的数据包，包含执行结果和可能的异常信息
            try {
                packager.send(pkg);// 将数据包发送回客户端
            } catch (Exception e1) {
                e1.printStackTrace();// 如果在发送数据包时发生异常，打印异常信息并结束循环
                break;
            }
        }
        exe.close();// 关闭Executor
        try {
            packager.close();// 关闭Packager
        } catch (Exception e) {
            e.printStackTrace();// 如果在关闭Packager时发生异常，打印异常信息
        }
    }

}