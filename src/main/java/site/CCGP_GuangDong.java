package site;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

public class CCGP_GuangDong extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_GuangDong.class);
    private static String relu = "{\"0006\": {\n" +
            "    \"authorRelu\": \"p:has(span:has(span:containsOwn(1.釆购人信息))) + p span:eq(1) span|span:containsOwn(采购人名称：)|p:has(span:containsOwn(1.釆购人信息)) + p span|span:containsOwn(采购人：)\"},\n" +
            "    \"0005\": {\n" +
            "        \"authorRelu\": \"h6:containsOwn(1.釆购人信息) + p|span:containsOwn(采购人名称：)|p:has(span:containsOwn(1.釆购人信息)) + p span\"},\n" +
            "    \"-3\": {\"authorRelu\": \"span:containsOwn(采购人名称：) + span\"},\n" +
            "    \"0008\": {\n" +
            "        \"authorRelu\": \"p:has(span:has(span:containsOwn(1.釆购人信息))) + p span:eq(1) span|span:containsOwn(采购人名称：)|p:has(span:containsOwn(1.釆购人信息)) + p span|span:containsOwn(采购人：)\"}}";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        String url = "http://www.ccgp-guangdong.gov.cn/queryMoreInfoList.do";
        final String[] channelCodes = {"0005", "0006", "0008", "-3"};
        String start_time = Util.getLastMonth(null, 3), end_time = Util.getLastMonth(null, 0);
        JSONArray citys = JSONArray.parseArray(Bidding.properties.getProperty("ccgp.guangdong.city"));
        List<String> urls = new ArrayList<String>();
        for (String channelCode : channelCodes) {
            for (int i = 0; i < citys.size(); i++) {
                urls.add(url.concat("&#44channelCode=".concat(channelCode).concat("&operateDateFrom=".concat(start_time).concat("&operateDateTo=".concat(end_time).concat("&performOrgName=&poor=&purchaserOrgName=&sitewebId=".concat(citys.getString(i)).concat("&stockIndexName=&stockNum=&stockTypes=&title=&pageIndex=1&pageSize=15"))))));
            }
        }
        this.main(urls.toArray(new String[urls.size()]));
        urls.clear();
        JSONArray diqus = null;
        for (int i = 0; i < citys.size(); i++) {
            diqus = JSONArray.parseArray(Bidding.properties.getProperty("ccgp.guangdong.".concat(citys.getString(i))));
            if (diqus.size() <= 1){
                continue;
            }
            for (int s = 0; s < diqus.size(); s++) {
                for (String channelCode : channelCodes) {
                    urls.add(url.concat("&#44channelCode=".concat(channelCode).concat("&operateDateFrom=".concat(start_time).concat("&operateDateTo=".concat(end_time).concat("&performOrgName=&poor=&purchaserOrgName=&sitewebId=".concat(diqus.getString(i)).concat("&stockIndexName=&stockNum=&stockTypes=&title=&pageIndex=1&pageSize=15"))))));
                }
            }
            this.main(urls.toArray(new String[urls.size()]));
            urls.clear();
        }
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 10;
        fullcontentRelu = "div.zw_c_c_cont";
        nodeListRelu = "ul.m_m_c_list li";
        titleRelu = "div.zw_c_c_title";
        priceRelu = "span:containsOwn(预算金额：)";
        addTimeRelu = "em";
        addTimeParse = "yyyy-MM-dd";
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
            this.baseUrl = url.split("/queryMoreInfoList")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("pageIndex=(\\d+)", "pageIndex=" + (currentPage + 1));
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null || httpBody.contains("data\":[]")) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = null;
            try {
                pageSource = getHttpBody(retryTime, tempUrl);
                Document parse = Jsoup.parse(pageSource);
                String colcode = Util.match("channelCode=(.*?)&", url)[1];
                extract(parse, data, colcode);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
                    currentPage = Integer.parseInt(Util.match("pageIndex=(\\d+)", url)[1]);
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
    protected String getPrice(Document parse) {
        String price = null;
        try {
            price = parse.select(this.priceRelu).get(0).text();
            price = price.contains("：")? price.split("：")[1]: price;
        } catch (Exception e) {
            return "";
        }
        return price;
    }

    @Override
    protected void extract(Document parse, StructData data, String colcode) {
        logger.info("==================================");
        String title = getTitle(parse);
        data.setTitle(title);
        String author = getAuthor(parse, colcode);
        logger.info("author: " + author);
        data.setAuthor(author);
        int catIdByText = -1;
        try {
            catIdByText = getCatIdByText(title);
            logger.info("catId: " + catIdByText);
        } catch (Exception ignore) {
        }
        data.setCat_id(catIdByText);
        logger.info("cityId: " + cityIdRelu);
        data.setDescription(title);
        String price = getPrice(parse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    protected String getAuthor(Document parse, String query_sign) {
        String author = "";
        try {
            String[] authorRelus = relus.getJSONObject(query_sign).getString("authorRelu").split("\\|");
            for (String authorRelu : authorRelus) {
                try {
                    author = parse.select(authorRelu).get(0).text();
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (author.length() > 0 && !author.equals("")) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return author;
    }

    @Override
    protected String getUrl(Element element) {
        Element a = null;
        String href = null;
        try {
            a = element.select("a").get(1);
            href = this.baseUrl + a.attr("href");
        } catch (Exception e) {
            return "";
        }
        return href;
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        Elements cListBid = parse.select(this.nodeListRelu);
        for (Element element : cListBid) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                Date date = getAddTime(element);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    return allResults;
                }
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                String url = getUrl(element);
                if (url.equals("") || url == null) {
                    continue;
                }
                logger.info("url: " + url);
                resultData.setArticleurl(url);
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }
}
