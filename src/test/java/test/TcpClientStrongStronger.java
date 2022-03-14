package test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TcpClientStrongStronger implements Runnable {

    public static void main(String[] args) {
        // 种多个线程发起Socket客户端连接请求
        for (int i = 0; i < 1; i++) {
            TcpClientStrongStronger c = new TcpClientStrongStronger();

            new Thread(c).start();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    static AtomicLong minigps_counter = new AtomicLong(0);

    @Override
    public void run() {
        int a = 63000;
        SocketChannel[] channels = new SocketChannel[a];

        Selector selector = null;
        try {
            selector = Selector.open();
            for (int i = 0; i < a; i++) {
                try {
                    channels[i] = SocketChannel.open();
                    channels[i].configureBlocking(false);
                    channels[i].connect(new InetSocketAddress("192.168.1.197", 8810));
                    channels[i].register(selector, SelectionKey.OP_CONNECT);
                    selector.select();
                    Iterator ite = selector.selectedKeys().iterator();
                    while (ite.hasNext()) {
                        SelectionKey key = (SelectionKey) ite.next();
                        ite.remove();
                        SocketChannel schannel = (SocketChannel) key.channel();
                        if (key.isConnectable()) {

                            if (schannel.isConnectionPending()) {
                                if (schannel.finishConnect()) {
                                    // 只有当连接成功后才能注册OP_READ事件
                                    key.interestOps(SelectionKey.OP_READ);
                                    long devId = minigps_counter.incrementAndGet();
                                    System.out.println(devId);
                                    String json = "{\"cmd\":\"reqConnect\",\"devId\":\"" + devId + "\",\"sCode\":\"A51FAD\",\"hbRate\":120,\"devInfo\":{\"IMEI\":\"999999999999995\",\"IMSI\":\"999999999999995\",\"productId\":\"KW02\",\"fwVer\":\"KW02_buga_01_hv01_sv01\",\"fwBuild\":1,\"mcuBuild\":1,\"btName\":\"KW02_bt\"}}";
                                    String cmd = json.length() + "|0|KW02|20|56|reqConnect||" + json + "|";
                                    String words = "MG|" + cmd.length() + "|" + cmd;

                                    schannel.write(CharsetHelper.encode(CharBuffer.wrap(words)));
                                    //sleep(500);
                                } else {
                                    key.cancel();
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    i--;
                }
            }

            while (true) {
                selector.select();
                Iterator ite = selector.selectedKeys().iterator();
                while (ite.hasNext()) {
                    SelectionKey key = (SelectionKey) ite.next();
                    ite.remove();
                    SocketChannel schannel = (SocketChannel) key.channel();

                    if (key.isReadable()) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
                        schannel.read(byteBuffer);
                        byteBuffer.flip();
                        CharBuffer charBuffer = CharsetHelper.decode(byteBuffer);
                        String answer = charBuffer.toString();
                        System.out.println(Thread.currentThread().getId() + "---" + answer);
                        String json2 = "{\"cmd\":\"heartbeat\",\"hbRate\": 120}";
                        String cmd2 = json2.length() + "|0|KW02|20|111|heartbeat||" + json2 + "|";
                        String word = "MG|" + cmd2.length() + "|" + cmd2;
                        schannel.write(CharsetHelper.encode(CharBuffer.wrap(word)));
                        sleep(10000);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sleep(long l) {
        try {
            TimeUnit.MICROSECONDS.sleep(l);
            ;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static class CharsetHelper {

        private static final String UTF_8 = "UTF-8";

        private static CharsetEncoder encoder = Charset.forName(UTF_8).newEncoder();

        private static CharsetDecoder decoder = Charset.forName(UTF_8).newDecoder();

        public static ByteBuffer encode(CharBuffer in) throws CharacterCodingException {
            return encoder.encode(in);
        }

        public static CharBuffer decode(ByteBuffer in) throws CharacterCodingException {
            return decoder.decode(in);
        }
    }
}
