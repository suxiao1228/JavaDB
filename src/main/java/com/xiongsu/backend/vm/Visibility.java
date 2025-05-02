package com.xiongsu.backend.vm;

import com.xiongsu.backend.tm.TransactionManager;

import java.lang.annotation.Repeatable;

public class Visibility {

    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e) {
        long xmax = e.getXmax();//获取条目的删除版本号
        if (t.level == 0) {//如果事务的隔离级别为0,即读未提交，那么不跳过该版本，返回false
            return false;
        } else {
            //如果事务的隔离级别为0,那么检查删除版本是否已提交，并且删除版本号大于事务的ID或者删除版本号在事务的快照中
            //如果满足上述条件，那么跳过该版本，返回true
            return tm.isCommited(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }
    }

    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e) {
        if (t.level == 0) {
            return readCommitted(tm, t, e);
        } else {
            return repeatableRead(tm, t, e);
        }
    }

    //用来在读提交的隔离级别下，某个记录是否对事务t可见
    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;//获取事务ID
        long xmin = e.getXmin();//获取记录的创建版本号
        long xmax = e.getXmax();//获取记录的删除版本号
        if (xmin == xid && xmax == 0) return true;//如果记录的创建版本号等于事务的ID并且记录未被删除，则返回true

        if (tm.isCommited(xmin)) {//如果记录的创建版本已经提交
            if (xmax == 0) return true;//如果记录未被删除，则返回true
            if (xmax != xid) {//如果记录的删除版本号不等于事务的ID
                //如果记录的删除版本未提交，则返回true
                //因为没有提交，代表该数据还是上一个版本可见的
                if (!tm.isCommited(xmax)) {
                    return true;
                }
            }
        }
        //其他情况返回false
        return false;
    }

    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;//获取事务的ID
        long xmin = e.getXmin();//获取条目的创建版本号
        long xmax = e.getXmax();//获取条目的删除版本号
        if (xmin == xid && xmax == 0) return true;// 如果条目的创建版本号等于事务的ID并且条目未被删除，则返回true

        // 如果条目的创建版本已经提交，并且创建版本号小于事务的ID，并且创建版本号不在事务的快照中
        if (tm.isCommited(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            if (xmax == 0) return true;// 如果条目未被删除，则返回true
            if (xmax != xid) {// 如果条目的删除版本号不等于事务的ID
                // 如果条目的删除版本未提交，或者删除版本号大于事务的ID，或者删除版本号在事务的快照中，则返回true
                if (!tm.isCommited(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        //其他情况返回false
        return false;
    }
}
