package site;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.SqlPool;
import util.Util;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_ChongQing extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_ChongQing.class);
    private static JSONObject cats;
    private static HashMap<String, String> idMap = new HashMap<String, String>();

    @Override
    protected void setValue() {
        // 采集类型id规则
        catIdRelu = "h2#titlecandel";
        // 城市id规则
        cityIdRelu = 21;
        // 价格规则
        priceRelu = "p:matchesOwn(成交金额：)";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd HH:mm:ss";
        // 内容规则
        fullcontentRelu = "div.wrap-post ng-scope";

        idMap.put("100", "公开招标");
        idMap.put("200", "邀请招标");
        idMap.put("300", "竞争性谈判");
        idMap.put("400", "询价");
        idMap.put("500", "单一来源");
        idMap.put("800", "竞争性磋商");
        idMap.put("6001", "协议竞价");
        idMap.put("6003", "网上询价");

    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.chongqing.url").split(",");
        for (int i = 0; i < urls.length; i++) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            String endDate = format.format(new Date(System.currentTimeMillis()));
            String startDate = format.format(new Date(System.currentTimeMillis() - 3 * 30 * 24 * 60 * 60 * 1000L));
            urls[i] = urls[i].replace("2021-05-29", endDate).replace("2021-02-28", startDate);
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void main(String[] urls) {
        int retryTime = 3;
        SimpleDateFormat format = null;
        try {
            String retryTimes = Bidding.properties.getProperty("download_retry_times");
            retryTime = Integer.parseInt(retryTimes);
        } catch (Exception ignore) {
        }
        try {
            String deadDateParse = Bidding.properties.getProperty("dead.date");
            format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date parse = format.parse(deadDateParse);
            long time = parse.getTime();
            this.deadDate = new Date(time);
        } catch (Exception ignore) {
            try {
                format = new SimpleDateFormat("yyyy-MM-dd");
                this.deadDate = format.parse(Util.getLastMonth(null, 3));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        for (String url : urls) {
            logger.info("当前开始url： " + url);
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = null;
            int nums = 5;
            while (nums > 0) {
                try {
                    pageSource = getHttpBody(retryTime, tempUrl);
                    if (pageSource.contains("502 Bad Gateway")) {
                        nums = nums - 1;
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    nums = nums - 1;
                }
            }
            Document parse = Jsoup.parse(pageSource);
            extract(parse, data, pageSource);
        }
        int count = 0;
        for (StructData resultData : allResult) {
            try {
                String sql;
                String tableName = Bidding.properties.getProperty("table.name");
                if (tableName == null) {
                    sql = Util.getInsertSql("fa_article", StructData.class, resultData);
                } else {
                    sql = Util.getInsertSql(tableName, StructData.class, resultData);
                }
                SqlPool.getInstance().getStatement().execute(sql);
                logger.info("当前插入第：" + count + " 条");
                ++count;
            } catch (SQLException e) {
                logger.error("插入数据错误：" + e, e);
            }
        }
        logger.info("插入成功：" + count + " 条");
        //添加下一页
        if ((allResult.size() - count <= allResult.size() - 1) && (allResult.size() > 0)) {
            try {
                try {
                    currentPage = Integer.parseInt(Util.match("index_(\\d+)", url)[1]);
                } catch (Exception ignore) {
                }
                String nextPageUrl = getNextPageUrl(document, currentPage, httpBody, url);
                if (nextPageUrl != null && (!"".equals(nextPageUrl))) {
                    startRun(retryTime, nextPageUrl, (currentPage + 1));
                }
            } catch (Exception e) {
                logger.error("下一页提取错误：" + e, e);
            }
        }
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        try {
            logger.info("==================================");
            JSONObject jsonObject = JSONObject.parseObject(pageSource);
            String string = jsonObject.getJSONObject("notice").getString("html");
            data.setFullcontent(string);
            logger.info(string);
        } catch (Exception e) {
            logger.error("提取内容出错：" + e, e);
        }
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select("p:matchesOwn(成交金额：)").get(0).text().replaceAll("成交金额：", "");
        } catch (Exception e) {
            try {
                return parse.select("p:matchesOwn(中标金额：)").get(0).text().replaceAll("中标金额：", "");
            } catch (Exception ex) {
                return "";
            }
        }
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONObject jsonObject = JSONObject.parseObject(httpBody);
            JSONArray notices = jsonObject.getJSONArray("notices");
            for (int i = 0; i < notices.size(); i++) {
                StructData structData = new StructData();
                JSONObject curObj = notices.getJSONObject(i);
                String title = curObj.getString("title");
                structData.setTitle(title);
                String id = curObj.getString("id");
                String url = "https://www.ccgp-chongqing.gov.cn/gwebsite/api/v1/notices/stable/" + id + "?__platDomain__=www.ccgp-chongqing.gov.cn";
                structData.setArticleurl(url);
                String issueTime = curObj.getString("issueTime");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date time = format.parse(issueTime);
                if (time.getTime() - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    return allResults;
                }
                String projectPurchaseWay = curObj.getString("projectPurchaseWay");
                try {
                    int catId = getCatIdByText(idMap.get(projectPurchaseWay));
                    structData.setCat_id(catId);
                } catch (Exception e) {
                    structData.setCat_id(-1);
                }
                structData.setAdd_time(time.getTime());
                structData.setAdd_time_name(issueTime);
                structData.setCity_id(this.cityIdRelu);
                logger.info("url: " + url);
                logger.info("title: " + title);
                logger.info("catId: " + structData.getCat_id());
                allResults.add(structData);
            }
        } catch (Exception e) {
            logger.error("json 解析失败");
        }
        return allResults;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            if (url.contains("&pi=")) {
                String id = Util.match("&pi=(\\d+)", url)[1];
                nextPageUrl = url.replaceAll("&pi=\\d+", "&pi=" + (Integer.parseInt(id) + 1));
            } else if (url.contains("page=")) {
                String id = Util.match("page=(\\d+)", url)[1];
                nextPageUrl = url.replaceAll("page=\\d+", "page=" + (Integer.parseInt(id) + 1));
            } else {
                String id = Util.match("current=(\\d+)", url)[1];
                nextPageUrl = url.replaceAll("current=\\d+", "current=" + (Integer.parseInt(id) + 1));
            }
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }
}