package com.xiongsu.backend.tbm;

import com.google.common.primitives.Bytes;
import com.xiongsu.backend.parser.statement.*;
import com.xiongsu.backend.tm.TransactionManagerImpl;
import com.xiongsu.backend.utils.Panic;
import com.xiongsu.backend.utils.ParseStringRes;
import com.xiongsu.backend.utils.Parser;
import com.xiongsu.common.Error;

import java.util.*;

/**
 * Table 维护了表结构
 * 二进制结构如下:
 * [TableName] [NextTable]
 * [Field1Uid] [Field2Uid]...[FieldNUid]
 */
public class Table {
    TableManager tbm;// 表管理器，用于管理数据库表
    long uid;// 表的唯一标识符
    String name;// 表的名称
    byte status;// 表的状态
    long nextUid;// 下一个表的唯一标识符
    List<Field> fields = new ArrayList<>();// 表的字段列表

    //这个静态方法用于从数据库中加载一个表
    public static Table loadTable(TableManager tbm, long uid) {
        byte[] raw = null;// 初始化一个字节数组用于存储从数据库中读取的原始数据
        try {
            raw = ((TableManagerImpl)tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);// 使用表管理器的版本管理器从数据库中读取指定uid的表的原始数据
        } catch (Exception e) {
            Panic.panic(e);// 如果在读取过程中发生异常，调用Panic.panic方法处理异常
        }
        assert raw != null;// 断言原始数据不为空
        Table tb = new Table(tbm, uid);// 创建一个新的表对象
        return tb.parseSelf(raw);// 使用原始数据解析表对象，并返回这个表对象
    }

    /**
     * 创建一个新的数据库表
     * @param tbm        表管理器，用于管理数据库表
     * @param nextUid    下一个表的唯一标识符
     * @param xid        事务ID
     * @param create     创建表的语句
     * @return           创建的表
     * @throws Exception
     */
    public static Table createTable(TableManager tbm, long nextUid, long xid, Create create) throws Exception {
        Table tb = new Table(tbm, create.tableName, nextUid); // 创建一个新的表的对象
        for(int i = 0; i < create.fieldName.length; i ++) {// 遍历创建表语句中的所有字段
            //获取字段名和字段类型
            String fieldName = create.fieldName[i];
            String fieldType = create.fieldType[i];
            //判断该字段是否需要建立索引
            boolean indexed = false;
            for(int j = 0; j < create.index.length; j ++) {
                if(fieldName.equals(create.index[j])) {
                    indexed = true;
                    break;
                }
            }
            tb.fields.add(Field.createField(tb, xid, fieldName, fieldType, indexed));// 创建一个新的字段对象，并添加到表对象中
        }

        return tb.persistSelf(xid);// 将表对象的状态持久化到存储系统中，并返回表对象
    }

    public Table(TableManager tbm, long uid) {
        this.tbm = tbm;
        this.uid = uid;
    }

    public Table(TableManager tbm, String tableName, long nextUid) {
        this.tbm = tbm;
        this.name = tableName;
        this.nextUid = nextUid;
    }

    //这个方法用于解析表对象
    // [TableName] [NextTable] [Field1Uid][Field2Uid]...[FieldNUid]
    private Table parseSelf(byte[] raw) {
        int position = 0;// 初始化位置变量
        ParseStringRes res = Parser.parseString(raw);// 使用Parser.parseString方法解析原始数据中的字符串
        name = res.str;// 将解析出的字符串赋值给表的名称
        position += res.next;// 更新位置变量
        nextUid = Parser.parseLong(Arrays.copyOfRange(raw, position, position+8));// 使用Parser.parseLong方法解析原始数据中的长整数，并赋值给下一个uid
        position += 8;// 更新位置变量

        // 当位置变量小于原始数据的长度时，继续循环
        while(position < raw.length) {
            long uid = Parser.parseLong(Arrays.copyOfRange(raw, position, position+8));// 使用Parser.parseLong方法解析原始数据中的长整数，并赋值给uid
            position += 8;// 更新位置变量
            fields.add(Field.loadField((Table) this, uid));// 使用Field.loadField方法加载字段，并添加到表的字段列表中
        }
        return this;// 返回当前表对象
    }

    /**
     * 将Table对象的状态持久化到存储系统中， [TableName] [NextTable] [Field1Uid][Field2Uid]...[FieldNUid]
     * @param xid 事务ID
     * @return 当前Table对象
     * @throws Exception
     */
    private Table persistSelf(long xid) throws Exception {
        byte[] nameRaw = Parser.string2Byte(name);// 将表名转换为字节数组
        byte[] nextRaw = Parser.long2Byte(nextUid);// 将下一个uid转换为字节数组
        byte[] fieldRaw = new byte[0];// 创建一个空的字节数组，用于存储字段的uid
        for(Field field : fields) {// 遍历所有的字段
            fieldRaw = Bytes.concat(fieldRaw, Parser.long2Byte(field.uid));// 将字段的uid转换为字节数组，并添加到fieldRaw中
        }
        uid = ((TableManagerImpl)tbm).vm.insert(xid, Bytes.concat(nameRaw, nextRaw, fieldRaw));// 将表名、下一个uid和所有字段的uid插入到存储系统中，返回插入的uid
        return this;// 返回当前Table对象
    }

    public int delete(long xid, Delete delete) throws Exception {
        List<Long> uids = parseWhere(delete.where);
        int count = 0;
        for (Long uid : uids) {
            if(((TableManagerImpl)tbm).vm.delete(xid, uid)) {
                count ++;
            }
        }
        return count;
    }

    public int update(long xid, Update update) throws Exception {
        List<Long> uids = parseWhere(update.where);
        Field fd = null;
        for (Field f : fields) {
            if(f.fieldName.equals(update.fieldName)) {
                fd = f;
                break;
            }
        }
        if(fd == null) {
            throw Error.FieldNotFoundException;
        }
        Object value = fd.string2Value(update.value);
        int count = 0;
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl)tbm).vm.read(xid, uid);
            if(raw == null) continue;

            ((TableManagerImpl)tbm).vm.delete(xid, uid);

            Map<String, Object> entry = parseEntry(raw);
            entry.put(fd.fieldName, value);
            raw = entry2Raw(entry);
            long uuid = ((TableManagerImpl)tbm).vm.insert(xid, raw);

            count ++;

            for (Field field : fields) {
                if(field.isIndexed()) {
                    field.insert(entry.get(field.fieldName), uuid);
                }
            }
        }
        return count;
    }

    public String read(long xid, Select read) throws Exception {
        List<Long> uids = parseWhere(read.where);
        StringBuilder sb = new StringBuilder();
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl)tbm).vm.read(xid, uid);
            if(raw == null) continue;
            Map<String, Object> entry = parseEntry(raw);
            sb.append(printEntry(entry)).append("\n");
        }
        return sb.toString();
    }

    public void insert(long xid, Insert insert) throws Exception {
        Map<String, Object> entry = string2Entry(insert.values);
        byte[] raw = entry2Raw(entry);
        long uid = ((TableManagerImpl)tbm).vm.insert(xid, raw);
        for (Field field : fields) {
            if(field.isIndexed()) {
                field.insert(entry.get(field.fieldName), uid);
            }
        }
    }

    private Map<String, Object> string2Entry(String[] values) throws Exception {
        if(values.length != fields.size()) {
            throw Error.InvalidValuesException;
        }
        Map<String, Object> entry = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            Object v = f.string2Value(values[i]);
            entry.put(f.fieldName, v);
        }
        return entry;
    }

    /**
     * 解析WHERE 子句并返回满足条件的记录的uid列表
     * @param where
     * @return
     * @throws Exception
     */
    private List<Long> parseWhere(Where where) throws Exception {
        // 初始化搜索范围和标志位
        long l0=0, r0=0, l1=0, r1=0;
        boolean single = false;
        Field fd = null;
        // 如果 WHERE 子句为空，则搜索所有记录
        if(where == null) {
            // 寻找第一个有索引的字段
            for (Field field : fields) {
                if(field.isIndexed()) {
                    fd = field;
                    break;
                }
            }
            // 设置搜索范围为整个 uid 空间
            l0 = 0;
            r0 = Long.MAX_VALUE;
            single = true;
        } else {
            // 如果 WHERE 子句不为空，则根据 WHERE 子句解析搜索范围
            // 寻找 WHERE 子句中涉及的字段
            for (Field field : fields) {
                if(field.fieldName.equals(where.singleExp1.field)) {
                    // 如果字段没有索引，则抛出异常
                    if(!field.isIndexed()) {
                        throw Error.FieldNotIndexedException;
                    }
                    fd = field;
                    break;
                }
            }
            // 如果字段不存在，则抛出异常
            if(fd == null) {
                throw Error.FieldNotFoundException;
            }
            // 计算 WHERE 子句的搜索范围
            CalWhereRes res = calWhere(fd, where);
            l0 = res.l0; r0 = res.r0;
            l1 = res.l1; r1 = res.r1;
            single = res.single;
        }
        // 在计算出的搜索范围内搜索记录
        List<Long> uids = fd.search(l0, r0);
        // 如果 WHERE 子句包含 OR 运算符，则需要搜索两个范围，并将结果合并
        if(!single) {
            List<Long> tmp = fd.search(l1, r1);
            uids.addAll(tmp);
        }
        return uids;// 返回搜索结果
    }

    class CalWhereRes {
        long l0, r0, l1, r1;
        boolean single;
    }

    private CalWhereRes calWhere(Field fd, Where where) throws Exception {
        CalWhereRes res = new CalWhereRes();
        switch(where.logicOp) {
            case "":
                res.single = true;
                FieldCalRes r = fd.calExp(where.singleExp1);
                res.l0 = r.left; res.r0 = r.right;
                break;
            case "or":
                res.single = false;
                r = fd.calExp(where.singleExp1);
                res.l0 = r.left; res.r0 = r.right;
                r = fd.calExp(where.singleExp2);
                res.l1 = r.left; res.r1 = r.right;
                break;
            case "and":
                res.single = true;
                r = fd.calExp(where.singleExp1);
                res.l0 = r.left; res.r0 = r.right;
                r = fd.calExp(where.singleExp2);
                res.l1 = r.left; res.r1 = r.right;
                if(res.l1 > res.l0) res.l0 = res.l1;
                if(res.r1 < res.r0) res.r0 = res.r1;
                break;
            default:
                throw Error.InvalidLogOpException;
        }
        return res;
    }

    private String printEntry(Map<String, Object> entry) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            sb.append(field.printValue(entry.get(field.fieldName)));
            if(i == fields.size()-1) {
                sb.append("]");
            } else {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private Map<String, Object> parseEntry(byte[] raw) {
        int pos = 0;
        Map<String, Object> entry = new HashMap<>();
        for (Field field : fields) {
            Field.ParseValueRes r = field.parserValue(Arrays.copyOfRange(raw, pos, raw.length));
            entry.put(field.fieldName, r.v);
            pos += r.shift;
        }
        return entry;
    }

    private byte[] entry2Raw(Map<String, Object> entry) {
        byte[] raw = new byte[0];
        for (Field field : fields) {
            raw = Bytes.concat(raw, field.value2Raw(entry.get(field.fieldName)));
        }
        return raw;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append(name).append(": ");
        for(Field field : fields) {
            sb.append(field.toString());
            if(field == fields.get(fields.size()-1)) {
                sb.append("}");
            } else {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
