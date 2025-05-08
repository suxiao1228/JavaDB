package com.xiongsu.client;

import java.util.Scanner;

//用于接受用户的输入，并调用`Client.execute()`
public class Shell {
    private Client client;

    public Shell(Client client) {
        this.client = client;
    }

    // 定义一个运行方法，用于启动客户端的交互式命令行界面
    public void run() {
        Scanner sc = new Scanner(System.in);// 创建一个Scanner对象，用于读取用户的输入
        try {
            while(true) {// 循环接收用户的输入，直到用户输入"exit"或"quit"
                System.out.print(":> "); // 打印提示符
                String statStr = sc.nextLine();// 读取用户的输入
                if("exit".equals(statStr) || "quit".equals(statStr)) {// 如果用户输入"exit"或"quit"，则退出循环
                    break;
                }
                // 尝试执行用户的输入命令，并打印执行结果
                try {
                    byte[] res = client.execute(statStr.getBytes());// 将用户的输入转换为字节数组，并执行
                    System.out.println(new String(res));// 将执行结果转换为字符串，并打印
                } catch(Exception e) {// 如果在执行过程中发生异常，打印异常信息
                    System.out.println(e.getMessage());
                }

            }
            // 无论是否发生异常，都要关闭Scanner和Client
        } finally {
            sc.close();// 关闭Scanner
            client.close();// 关闭Client
        }
    }
}
