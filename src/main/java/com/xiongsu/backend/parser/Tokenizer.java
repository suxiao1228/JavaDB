package com.xiongsu.backend.parser;

import com.xiongsu.common.Error;

//Tokenizer类用于对语句进行逐字节解析，根据空白符或者特定的此法规则，将语句切割成多个token
//提供了ppek()和pop()方法，方便去除Token进行解析
//具体的切割实现在内部


//这里的token分词采用的本质上是逐层判断的过程，没有使用状态机，如果有时间后续可能考虑改进。但这毕竟不是本次数据库项目的核心，因此也不过多展开
public class Tokenizer {
    private byte[] stat;
    private int pos;
    private String currentToken;
    private boolean flushToken;
    private Exception err;

    public Tokenizer(byte[] stat) {
        this.stat = stat;
        this.pos = 0;
        this.currentToken = "";
        this.flushToken = true;
    }

    /**
     * 获取当前的标记，如果需要的化，会生成新的标记
     * @return
     * @throws Exception
     */
    public String peek() throws Exception {
        if(err != null) {
            throw err;
        }
        if(flushToken) {
            String token = null;
            try {
                token = next();
            } catch(Exception e) {
                err = e;
                throw e;
            }
            currentToken = token;
            flushToken = false;
        }
        return currentToken;
    }

    /**
     * 将当前的标记设置为需要刷新，这样下次调用peek()时会生成新的标记
     */
    public void pop() {
        flushToken = true;
    }

    public byte[] errStat() {
        byte[] res = new byte[stat.length+3];
        System.arraycopy(stat, 0, res, 0, pos);
        System.arraycopy("<< ".getBytes(), 0, res, pos, 3);
        System.arraycopy(stat, pos, res, pos+3, stat.length-pos);
        return res;
    }

    /**
     * 跳过该字母，指向下一个字节
     */
    private void popByte() {
        pos ++;
        if(pos > stat.length) {
            pos = stat.length;
        }
    }

    private Byte peekByte() {
        if(pos == stat.length) {
            return null;
        }
        return stat[pos];
    }

    /**
     * 获取下一个标记
     * @return
     * @throws Exception
     */
    private String next() throws Exception {
        if(err != null) {
            throw err;//如果存在错误，抛出异常
        }
        return nextMetaState();//否则，获取下一个元状态
    }

    /**
     * 获取下一个元状态。元状态可以是一个符号，引号包围的字符串或者一个由字母，数字，下划线组成的标记
     * @return
     * @throws Exception
     */
    private String nextMetaState() throws Exception {
        while(true) {
            Byte b = peekByte();//获取下一个字节
            if(b == null) {
                return "";//如果没有下一个字节，返回空字符串
            }
            if(!isBlank(b)) {
                break;//如果下一个字节不是空白字符，跳出循环
            }
            popByte();//否则，跳过这个字节
        }
        byte b = peekByte();//获取下一个字节
        if(isSymbol(b)) {
            popByte();//如果这个字节是一个符号，跳过这个字节
            return new String(new byte[]{b});//并返回这个符号
        } else if(b == '"' || b == '\'') {
            return nextQuoteState();//如果这个字节是引号，获取下一个引号状态
        } else if(isAlphaBeta(b) || isDigit(b)) {
            return nextTokenState();//如果这个字节是字母，数字或下划线，获取下一个标记状态
        } else {
            err = Error.InvalidCommandException;// 否则，设置错误状态为无效的命令异常
            throw err;// 并抛出异常
        }
    }

    /**
     * 获取下一个标记，标记是由字母，数字或下划线组成的字符串
     * @return
     * @throws Exception
     */
    private String nextTokenState() throws Exception {
        StringBuilder sb = new StringBuilder();// 创建一个StringBuilder，用于存储标记
        while(true) {
            Byte b = peekByte();// 获取下一个字节
            // 如果没有下一个字节，或者下一个字节不是字母、数字或下划线，那么结束循环
            if(b == null || !(isAlphaBeta(b) || isDigit(b) || b == '_')) {
                if(b != null && isBlank(b)) {
                    popByte();
                }
                return sb.toString();// 返回标记
            }
            sb.append(new String(new byte[]{b}));// 如果下一个字节是字母、数字或下划线，那么将这个字节添加到StringBuilder中
            popByte();// 跳过这个字节
        }
    }

    static boolean isDigit(byte b) {
        return (b >= '0' && b <= '9');
    }

    static boolean isAlphaBeta(byte b) {
        return ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z'));
    }

    private String nextQuoteState() throws Exception {
        byte quote = peekByte();
        popByte();
        StringBuilder sb = new StringBuilder();
        while(true) {
            Byte b = peekByte();
            if(b == null) {
                err = Error.InvalidCommandException;
                throw err;
            }
            if(b == quote) {
                popByte();
                break;
            }
            sb.append(new String(new byte[]{b}));
            popByte();
        }
        return sb.toString();
    }

    static boolean isSymbol(byte b) {
        return (b == '>' || b == '<' || b == '=' || b == '*' ||
                b == ',' || b == '(' || b == ')');
    }

    static boolean isBlank(byte b) {
        return (b == '\n' || b == ' ' || b == '\t');
    }
}
