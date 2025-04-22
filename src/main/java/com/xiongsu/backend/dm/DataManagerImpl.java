package com.xiongsu.backend.dm;

import com.xiongsu.backend.common.AbstractCache;
import com.xiongsu.backend.dm.dataltem.DataItem;
import com.xiongsu.backend.dm.dataltem.DataItemImpl;
import com.xiongsu.backend.dm.logger.Logger;
import com.xiongsu.backend.dm.page.Page;
import com.xiongsu.backend.dm.page.PageOne;
import com.xiongsu.backend.dm.page.PageX;
import com.xiongsu.backend.dm.pageCache.PageCache;
import com.xiongsu.backend.dm.pageIndex.PageIndex;
import com.xiongsu.backend.dm.pageIndex.PageInfo;
import com.xiongsu.backend.tm.TransactionManager;
import com.xiongsu.backend.utils.Panic;
import com.xiongsu.backend.utils.Types;
import com.xiongsu.common.Error;

import javax.xml.crypto.Data;
import java.lang.reflect.Type;

public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager{

    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }

    @Override
    public DataItem read(long uid) throws Exception {
        //从缓存页面中读取到DataItemImpl
        DataItemImpl di = (DataItemImpl) super.get(uid);
        //检验di是否有效
        if (!di.isValid()) {
            //无效释放缓存
            di.release();
            return null;
        }
        return di;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        //将输入的数据包装成DataItem的原始格式
        byte[] raw = DataItem.wrapDataItemRaw(data);
        //如果数据项的大小超过了页面的最大空闲空间，抛出异常
        if (raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }

        //初始化一个页面信息对象
        PageInfo pi = null;
        //尝试5词找到一个可以容纳新数据项的页面
        for (int i = 0; i < 5; i++) {
            //从页面索引中选择一个可以容纳新数据项的页面
            pi = pIndex.select(raw.length);
            //如果找到了合适的页面，跳出循环
            if (pi != null) {
                break;
            } else {
                //如果没有找到合适的页面，创建一个新的页面，并将其添加到页面索引中
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        //如果还是没有找到合适的页面，抛出异常
        if (pi==null) {
            throw Error.DatabaseBusyException;
        }

        //初始化一个页面对象
        Page pg = null;
        //初始化空闲空间大小为0
        int freeSpace = 0;
        try {
            //获取页面信息对象中的页面
            pg = pc.getPage(pi.pgno);
            //生成插入日志
            byte[] log = Recover.insertLog(xid, pg, raw);
            //将日志写入日志文件
            logger.log(log);

            //在页面中插入新的数据项，并获取其在页面中的偏移量
            short offset = PageX.insert(pg, raw);

            //释放页面
            pg.release();
            //返回新插入的数据项的唯一标识符
            return Types.addressToUid(pi.pgno, offset);
        } finally {
            // 将取出的pg重新插入pIndex
            if (pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    //为xid生成update日志
    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        //从 uid中提取出偏移量(offset) ,这是通过位操作实现的，偏移量是uid的低16位
        // &运算： 有0则0，全1才1
        short offset = (short)(uid & ((1L << 16) - 1));
        //将uid右移32位，以便接下来提取出页面编号(pgno)
        uid >>>= 32;
        //从uid中提取出页面编号(pgno),页面编号是uid的高32位
        // &运算:有0则0，全1才1
        int pgno = (int)(uid & ((1L << 32) - 1));
        //使用页面缓存（pc）的getPage(int pgno)方法根据页面编号获取一个Page对象
        Page pg = pc.getPage(pgno);
        //使用DataItem接口的静态方法parseDataItem(Page pg, short offset, DataManagerImpl dm)
        //根据获取到的Page对象，偏移量和当前的DataManagerImpl对象(this)解析出一个DataItem对象，并返回这个对象
        return DataItem.parseDataItem(pg, offset, this);
    }

    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    //在创建文件时初始化PageOne
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    //在打开已有文件时时读入PageOne, 并验证正确性
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    /**
     * 填充PageIndex
     * 遍历从第二页开始的每一页，将每一页的页面编号和空闲空间大小添加到PageIndex中
     */

    //初始化pageIndex
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber(); //获取当前的页面数量
        for (int i = 2; i <= pageNumber; i++) { //从第二页开始，对每一页进行处理
            Page pg = null;
            try {
                pg = pc.getPage(i); // 尝试获取页面
            } catch (Exception e) {
                Panic.panic(e); //如果出现异常，处理异常
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));//将页面编号和页面的空闲空间大小添加到PageIndex中
            pg.release();//释放页面
        }
    }
}
