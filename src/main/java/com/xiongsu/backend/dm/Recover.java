package com.xiongsu.backend.dm;

import com.google.common.primitives.Bytes;
import com.xiongsu.backend.common.SubArray;
import com.xiongsu.backend.dm.dataltem.DataItem;
import com.xiongsu.backend.dm.logger.Logger;
import com.xiongsu.backend.dm.page.Page;
import com.xiongsu.backend.dm.page.PageX;
import com.xiongsu.backend.dm.pageCache.PageCache;
import com.xiongsu.backend.tm.TransactionManager;
import com.xiongsu.backend.utils.Panic;
import com.xiongsu.backend.utils.Parser;

import java.util.*;

public class Recover {

    private static final byte LOG_TYPE_INSERT = 0;
    private static final byte LOG_TYPE_UPDATE = 1;

    // updateLog:
    // [LogType] [XID] [UID] [OldRaw] [NewRaw]

    // insertLog:lll
    // [LogType] [XID] [Pgno] [Offset] [Raw]

    private static final int REDO = 0;
    private static final int UNDO = 1;

    static class InsertLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] raw;
    }

    static class UpdateLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] oldRaw;
        byte[] newRaw;
    }

    public static void recover(TransactionManager tm, Logger lg, PageCache pc) {
        System.out.println("Recovering...");

        lg.rewind();
        int maxPgno = 0;
        while(true) {
            byte[] log = lg.next();
            if(log == null) break;
            int pgno;
            if(isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                pgno = li.pgno;
            } else {
                UpdateLogInfo li = parseUpdateLog(log);
                pgno = li.pgno;
            }
            if(pgno > maxPgno) {
                maxPgno = pgno;
            }
        }
        if(maxPgno == 0) {
            maxPgno = 1;
        }
        pc.truncateByBgno(maxPgno);
        System.out.println("Truncate to " + maxPgno + " pages.");

        redoTranscations(tm, lg, pc);
        System.out.println("Redo Transactions Over.");

        undoTranscations(tm, lg, pc);
        System.out.println("Undo Transactions Over.");

        System.out.println("Recovery Over.");
    }

    private static void redoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        //重置日志文件的读取位置到开始
        lg.rewind();
        //循环读取日志文件中的所有日志记录
        while(true) {
            //读取下一条日志记录
            byte[] log = lg.next();
            //如果读取到的日志记录为空，标识已经读取到日志文件的末尾，跳出循环
            if(log == null) break;
            //判断日志记录的类型
            if(isInsertLog(log)) {
                //如果是插入日志，解析日志记录，获取插入日志信息
                InsertLogInfo li = parseInsertLog(log);
                //获取事务ID
                long xid = li.xid;
                //如果当前事务已经提交，进行重做操作
                if(!tm.isActive(xid)) {
                    doInsertLog(pc, log, REDO);
                }
            } else {
                //如果是更新日志，解析日志记录，获取更新日志信息
                UpdateLogInfo xi = parseUpdateLog(log);
                //获取事务ID
                long xid = xi.xid;
                //如果当前事务已经提交，进行重做操作
                if(!tm.isActive(xid)) {
                    doUpdateLog(pc, log, REDO);
                }
            }
        }
    }

    private static void undoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        // 创建一个用于存储日志的映射，键为事务ID，值为日志列表
        Map<Long, List<byte[]>> logCache = new HashMap<>();
        // 将日志文件的读取位置重置到开始
        lg.rewind();
        // 循环读取日志文件中的所有日志记录
        while(true) {
            // 读取下一条日志记录
            byte[] log = lg.next();
            // 如果读取到的日志记录为空，表示已经读取到日志文件的末尾，跳出循环
            if(log == null) break;
            // 判断日志记录的类型
            if(isInsertLog(log)) {
                // 如果是插入日志，解析日志记录，获取插入日志信息
                InsertLogInfo li = parseInsertLog(log);
                // 获取事务ID
                long xid = li.xid;
                // 如果当前事务仍然活跃，将日志记录添加到对应的日志列表中
                if(tm.isActive(xid)) {
                    if(!logCache.containsKey(xid)) {
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            } else {
                // 如果是更新日志，解析日志记录，获取更新日志信息
                UpdateLogInfo xi = parseUpdateLog(log);
                // 获取事务ID
                long xid = xi.xid;
                // 如果当前事务仍然活跃，将日志记录添加到对应的日志列表中
                if(tm.isActive(xid)) {
                    if(!logCache.containsKey(xid)) {
                        logCache.put(xid, new ArrayList<>());
                    }
                    // 将事务id对应的log添加到集合中
                    logCache.get(xid).add(log);
                }
            }
        }

        // 对所有活跃的事务的日志进行倒序撤销
        for(Map.Entry<Long, List<byte[]>> entry : logCache.entrySet()) {
            List<byte[]> logs = entry.getValue();
            for (int i = logs.size()-1; i >= 0; i --) {
                byte[] log = logs.get(i);
                // 判断日志记录的类型
                if(isInsertLog(log)) {
                    // 如果是插入日志，进行撤销插入操作
                    doInsertLog(pc, log, UNDO);
                } else {
                    // 如果是更新日志，进行撤销更新操作
                    doUpdateLog(pc, log, UNDO);
                }
            }
            // 中止当前事务
            tm.abort(entry.getKey());
        }
    }

    private static boolean isInsertLog(byte[] log) {
        return log[0] == LOG_TYPE_INSERT;
    }

    // [LogType] [XID] [UID] [OldRaw] [NewRaw]
    private static final int OF_TYPE = 0;
    private static final int OF_XID = OF_TYPE+1;
    private static final int OF_UPDATE_UID = OF_XID+8;
    private static final int OF_UPDATE_RAW = OF_UPDATE_UID+8;

    public static byte[] updateLog(long xid, DataItem di) {
        byte[] logType = {LOG_TYPE_UPDATE};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] uidRaw = Parser.long2Byte(di.getUid());
        byte[] oldRaw = di.getOldRaw();
        SubArray raw = di.getRaw();
        byte[] newRaw = Arrays.copyOfRange(raw.raw, raw.start, raw.end);
        return Bytes.concat(logType, xidRaw, uidRaw, oldRaw, newRaw);
    }

    private static UpdateLogInfo parseUpdateLog(byte[] log) {
        UpdateLogInfo li = new UpdateLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_UPDATE_UID));
        long uid = Parser.parseLong(Arrays.copyOfRange(log, OF_UPDATE_UID, OF_UPDATE_RAW));
        li.offset = (short)(uid & ((1L << 16) - 1));
        uid >>>= 32;
        li.pgno = (int)(uid & ((1L << 32) - 1));
        int length = (log.length - OF_UPDATE_RAW) / 2;
        li.oldRaw = Arrays.copyOfRange(log, OF_UPDATE_RAW, OF_UPDATE_RAW+length);
        li.newRaw = Arrays.copyOfRange(log, OF_UPDATE_RAW+length, OF_UPDATE_RAW+length*2);
        return li;
    }

    private static void doUpdateLog(PageCache pc, byte[] log, int flag) {
        int pgno;//用于存储页面编号
        short offset;//用于存储偏移量
        byte[] raw;//用于存储原始数据

        //根据标志位判断是进行重做操作还是撤销操作
        if(flag == REDO) {
            //如果是重做操作，解析日志记录，获取更新日志信息，主要获取新数据
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.newRaw;
        } else {
            //如果是撤销操作，解析日志记录，获取更新日志信息，主要获取旧数据
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.oldRaw;
        }
        Page pg = null;//用于存储获取到的页面
        try {
            //尝试从页面缓存中获取指定页码的页面
            pg = pc.getPage(pgno);
        } catch (Exception e) {
            //如果获取页面过程中发生异常，调用panic方法进行处理
            Panic.panic(e);
        }
        try {
            //在指定的页面和偏移量处插入解析出的数据，数据页缓存讲解了该方法
            PageX.recoverUpdate(pg, raw, offset);
        } finally {
            //无论是否发生异常，都要释放页面
            pg.release();
        }
    }

    // [LogType] [XID] [Pgno] [Offset] [Raw]
    private static final int OF_INSERT_PGNO = OF_XID+8;
    private static final int OF_INSERT_OFFSET = OF_INSERT_PGNO+4;
    private static final int OF_INSERT_RAW = OF_INSERT_OFFSET+2;

    public static byte[] insertLog(long xid, Page pg, byte[] raw) {
        byte[] logTypeRaw = {LOG_TYPE_INSERT};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] pgnoRaw = Parser.int2Byte(pg.getPageNumber());
        byte[] offsetRaw = Parser.short2Byte(PageX.getFSO(pg));
        return Bytes.concat(logTypeRaw, xidRaw, pgnoRaw, offsetRaw, raw);
    }

    private static InsertLogInfo parseInsertLog(byte[] log) {
        InsertLogInfo li = new InsertLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_INSERT_PGNO));
        li.pgno = Parser.parseInt(Arrays.copyOfRange(log, OF_INSERT_PGNO, OF_INSERT_OFFSET));
        li.offset = Parser.parseShort(Arrays.copyOfRange(log, OF_INSERT_OFFSET, OF_INSERT_RAW));
        li.raw = Arrays.copyOfRange(log, OF_INSERT_RAW, log.length);
        return li;
    }

    private static void doInsertLog(PageCache pc, byte[] log, int flag) {
        // 解析日志记录，获取插入日志信息
        InsertLogInfo li = parseInsertLog(log);
        Page pg = null;
        try {
            // 根据页码从页面缓存中获取页面，即AbstractCache.get()方法
            pg = pc.getPage(li.pgno);
        } catch(Exception e) {
            // 如果发生异常，调用Panic.panic方法处理
            Panic.panic(e);
        }
        try {
            // 如果标志位为UNDO，将数据项设置为无效
            if(flag == UNDO) {
                DataItem.setDataItemRawInvalid(li.raw);
            }
            // 在指定的页面和偏移量处插入数据
            PageX.recoverInsert(pg, li.raw, li.offset);
        } finally {
            // 无论是否发生异常，都要释放页面,即AbstractCache.release() 方法
            pg.release();
        }
    }
}
