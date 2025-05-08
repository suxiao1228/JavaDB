package com.xiongsu.transport;

public class Packager {
    private Transporter transporter;
    private Encoder encoder;

    public Packager(Transporter transporter, Encoder encoder) {
        this.transporter = transporter;
        this.encoder = encoder;
    }

    /**
     * 将信息编码之后发送
     * @param pkg
     * @throws Exception
     */
    public void send(Package pkg) throws Exception {
        byte[] data = encoder.encode(pkg);
        transporter.send(data);
    }

    /**
     * 将数据接收之后解密
     * @return
     * @throws Exception
     */
    public Package  receive() throws Exception {
        byte[] data = transporter.receive();
        return encoder.decode(data);
    }

    public void close() throws Exception {
        transporter.close();
    }
}
