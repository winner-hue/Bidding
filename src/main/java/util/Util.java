package util;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.sun.security.ntlm.Client;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import start.BiddingStart;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {
    private static Logger logger = LoggerFactory.getLogger(Util.class);

    public static Properties getProps(String path) {
        Properties props = new Properties();
        try {
            InputStream istream = BiddingStart.class.getResourceAsStream(path);
            props.load(istream);
            istream.close();
        } catch (IOException e) {
            logger.error("读取配置文件出错：" + e, e);
        }
        return props;
    }

    public static boolean isMatch(String pattern, String content) {
        Matcher m = Pattern.compile(pattern).matcher(content);
        return m.find();
    }

    public static String[] match(String pattern, String content) {
        Matcher m = Pattern.compile(pattern).matcher(content);

        while (m.find()) {
            int n = m.groupCount();
            String[] ss = new String[n + 1];
            for (int i = 0; i <= n; i++) {
                ss[i] = m.group(i);
            }
            return ss;
        }
        return null;
    }

    public static String stringToMD5(String plainText) {
        byte[] secretBytes = null;
        try {
            secretBytes = MessageDigest.getInstance("md5").digest(
                    plainText.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("没有这个md5算法！");
        }
        String md5code = new BigInteger(1, secretBytes).toString(16);
        for (int i = 0; i < 32 - md5code.length(); i++) {
            md5code = "0" + md5code;
        }
        return md5code;
    }

    public static <T> String getInsertSql(String tablename, Class<T> clazz, T t) {
        //insert into table_name (column_name1,column_name2, ...) values (value1,value2, ...)
        String sql = "";
        Field[] fields = ReflectUtil.getFieldsDirectly(clazz, false);
        StringBuffer topHalf = new StringBuffer("insert ignore into " + tablename + " (");
        StringBuffer afterAalf = new StringBuffer("values (");
        for (Field field : fields) {
            topHalf.append(field.getName() + ",");
            if (ReflectUtil.getFieldValue(t, field.getName()) instanceof String) {
                afterAalf.append("'" + ReflectUtil.getFieldValue(t, field.getName()) + "',");
            } else {
                afterAalf.append(ReflectUtil.getFieldValue(t, field.getName()) + ",");
            }
        }
        topHalf = new StringBuffer(StrUtil.removeSuffix(topHalf.toString(), ","));
        afterAalf = new StringBuffer(StrUtil.removeSuffix(afterAalf.toString(), ","));
        topHalf.append(") ");
        afterAalf.append(") ");
        sql = topHalf.toString() + afterAalf.toString();
        return sql;
    }

    /**
     * 获取指定月份的上一个月
     * dates：年份
     * month_num：月份
     */
    public static String getLastMonth(String dates, int month_num) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();
        if (dates == null) {
            Date date = new Date();
            calendar.setTime(date);
        } else {
            try {
                calendar.setTime(format.parse(dates));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            ; // 设置为当前时间
        }
        calendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH) - month_num); // 设置为指定前几个月
        Date date = calendar.getTime();
        String accDate = format.format(date);
        return accDate;
    }

    /**
     * 输出指定月份的所有日期
     * yearParam：年份  传入为0则默认输出当前月份的上一个月
     * monthParam：月份
     */
    public static List<String> getDayByMonth(int yearParam, int monthParam) {
        List list = new ArrayList();
        Calendar aCalendar = Calendar.getInstance(Locale.CHINA);
        aCalendar.set(yearParam, monthParam - 1, 1);
        int year = aCalendar.get(Calendar.YEAR);//年份
        int month = aCalendar.get(Calendar.MONTH) + 1;//月份
        int day = aCalendar.getActualMaximum(Calendar.DATE);
        for (int i = 1; i <= day; i++) {
            String aDate = null;
            if (month < 10 && i < 10) {
                aDate = String.valueOf(year) + "-0" + month + "-0" + i;
            }
            if (month < 10 && i >= 10) {
                aDate = String.valueOf(year) + "-0" + month + "-" + i;
            }
            if (month >= 10 && i < 10) {
                aDate = String.valueOf(year) + "-" + month + "-0" + i;
            }
            if (month >= 10 && i >= 10) {
                aDate = String.valueOf(year) + "-" + month + "-" + i;
            }
            list.add(aDate);
        }
        return list;
    }

    /**
     * 获取指定日期段内的所有日期
     */
    public static List<String> findDates(Date dBegin, Date dEnd) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List lDate = new ArrayList();
        try {
            lDate.add(sdf.format(dBegin.getTime()));
            Calendar calBegin = Calendar.getInstance();
            // 使用给定的 Date 设置此 Calendar 的时间
            calBegin.setTime(dBegin);
            Calendar calEnd = Calendar.getInstance();
            // 使用给定的 Date 设置此 Calendar 的时间
            calEnd.setTime(dEnd);
            // 测试此日期是否在指定日期之后
            while (dEnd.after(calBegin.getTime())) {
                // 根据日历的规则，为给定的日历字段添加或减去指定的时间量
                calBegin.add(Calendar.DAY_OF_MONTH, 1);
                String a = sdf.format(calBegin.getTime());
                lDate.add(a);
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return lDate;
    }

    public static String getRandomString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    public static boolean HasDigit(String content) {
        boolean flag = false;
        Pattern p = Pattern.compile(".*\\d+.*");
        Matcher m = p.matcher(content);
        if (m.matches()) {
            flag = true;
        }
        return flag;
    }
}
