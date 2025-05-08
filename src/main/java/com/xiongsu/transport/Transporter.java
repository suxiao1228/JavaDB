package com.xiongsu.transport;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.*;
import java.net.Socket;

public class Transporter {

    private Socket socket;
    private BufferedReader reader;// 字节缓冲流
    private BufferedWriter writer;

    public Transporter(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    /**
     * 发送数据
     * @param data
     * @throws Exception
     */
    public void send(byte[] data) throws Exception {
        String raw = hexEncode(data);
        writer.write(raw);
        writer.flush();
    }


    /**
     * 接收数据
     * @return
     * @throws Exception
     */
    public byte[] receive() throws Exception {
        String line = reader.readLine();
        if (line == null) {
            clone();
        }
        return hexDecode(line);
    }

    public void close() throws IOException {
        writer.close();
        reader.close();
        socket.close();
    }

    /**
     * 将字节数组转换为十六进制字符串
     * @param buf
     * @return
     */
    private String hexEncode(byte[] buf) {
        return Hex.encodeHexString(buf, true)+"\n";
    }

    /**
     * 将十六进制字符串转换成字节数组
     * @param buf
     * @return
     * @throws DecoderException
     */
    private byte[] hexDecode(String buf) throws DecoderException {
        return Hex.decodeHex(buf);
    }
}
