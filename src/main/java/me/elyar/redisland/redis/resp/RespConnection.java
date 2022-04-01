package me.elyar.redisland.redis.resp;

import me.elyar.redisland.redis.resp.type.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * 用于连接RESP协议的服务器
 *
 * @author e1y4r
 */
public class RespConnection {
    // RESP中的分隔符
    private static final String CRLF = "\r\n";
    // 连向服务器
    private Socket socket;
    // 用于像socket中输出数据
    private final PrintWriter printWriter;
    private Charset charset = Charset.forName("utf8");

    /**
     * 初始化连接
     *
     * @param host 服务器地址
     * @param port 端口号
     * @throws IOException 出现网络错误
     */
    public RespConnection(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setKeepAlive(true);
        printWriter = new PrintWriter(socket.getOutputStream(), true, charset);
    }

    /**
     * 向服务器发送数据，要发送的数据会被转化成RESP格式的数组再
     * 发送到服务器。
     *
     * @param arguments 要发送的数据
     */
    public void send(String... arguments) {
        int length = arguments.length;
        printWriter.print("*" + length + CRLF);
        for (String argument : arguments) {
            byte[] bytes = argument.getBytes();
            printWriter.print("$" + bytes.length + CRLF);
            printWriter.print(argument + CRLF);
        }
        printWriter.flush();
    }

    /**
     * 从服务器获取数据，根据返回的不同的类型，返回不同类型
     * 的{@code RespType}的数据。
     *
     * @return 从服务器获取到的数据
     * @throws IOException 出现网络错误
     */
    public RespType receive() throws IOException {
        InputStream inputStream = socket.getInputStream();
        char typeChar = getChar(inputStream);
        if (typeChar == '+') { // 简单字符串
            String simpleString = getLine(inputStream);
            return new RespString(simpleString);
        } else if (typeChar == '-') { // 错误信息
            String errorMessage = getLine(inputStream);
            return new RespError(errorMessage);
        } else if (typeChar == ':') { // 数字
            long integer = Long.parseLong(getLine(inputStream));
            return new RespInteger(integer);
        } else if (typeChar == '$') { // 指定长度的字符串
            int length = Integer.parseInt(getLine(inputStream));
            if (length == -1) {
                return new RespString(null);
            }
            String bulkString = getNChar(inputStream, length);
            return new RespString(bulkString);
        } else if (typeChar == '*') { // 数组类型
            int arrayLength = Integer.parseInt(getLine(inputStream));
            RespArray array = new RespArray(arrayLength);
            for (int i = 0; i < arrayLength; i++) {
                // 分别读取数组中的元素
                array.add(receive());
            }
            return array;
        } else if (typeChar == "\uFFFF".charAt(0)) {
            return null;
        } else {
            // 用于表示种类的字符无法被识别。
            throw new IllegalStateException(typeChar + "不是有效的种类字符！");
        }
    }

    /**
     * 从输入流中读取n个字符，组成字符串再返回。
     *
     * @param inputStream 输入流
     * @param length      要读取字符长度
     * @return 读取到的内容
     * @throws IOException 出现网络错误
     */
    public String getNChar(InputStream inputStream, int length) throws IOException {
        String r = new String(inputStream.readNBytes(length), charset);
        inputStream.readNBytes(2); // skip CRLF
        return r;
    }

    /**
     * 从输入流读取一行数据，一直读到出现CRLF(\r\n)为止。
     *
     * @param inputStream 输入流
     * @return 读到的一行数据
     * @throws IOException 出现网络错误
     */
    public String getLine(InputStream inputStream) throws IOException {
        char c = getChar(inputStream);
        StringBuilder line = new StringBuilder();
        while (c != '\n') {
            line.append(c);
            c = getChar(inputStream);
        }
        return line.substring(0, line.length() - 1);
    }

    /**
     * 读取输入流中的第一个字符
     *
     * @param inputStream 输入流
     * @return 输入流中的第一个字符
     * @throws IOException 出现网络错误
     */
    private char getChar(InputStream inputStream) throws IOException {
        return (char) inputStream.read();
    }

    /**
     * 关闭连接
     *
     * @throws IOException 出现网络错误
     */
    public void close() throws IOException {
        printWriter.close();
        socket.close();
    }
}
