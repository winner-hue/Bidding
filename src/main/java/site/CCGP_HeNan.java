package site;

import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.Download;
import util.SqlPool;
import util.Util;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_HeNan extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_HeNan.class);
    private String fujian = "";
    private static JSONObject cats;


    @Override
    protected void setValue() {
        titleRelu = "a";
        authorRelu = "tr:has(td:containsOwn(采购人信息)) + tr td|td:containsOwn(采购人：)";
        priceRelu = "td:matchesOwn(预算金额：)";
        addTimeRelu = "span.Gray.Right";
        addTimeParse = "yyyy-MM-dd HH:mm";
        fullcontentRelu = "table.Content";
        fjxxurlRelu = "div.List1.Top5 a";
        nodeListRelu = "div.List2 li";
        cityIdRelu = 25;
        catIdRelu = "a";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        cats = JSONObject.parseObject(Bidding.properties_cat.getProperty("henan_cat"));
        String[] urls = Bidding.properties.getProperty("ccgp.henan.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select(this.priceRelu).get(0).text().replaceAll(".*项目预算金额：", "");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("pageNo=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("pageNo=\\d+", "pageNo=" + (Integer.parseInt(id) + 1));
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }

    @Override
    protected String getUrl(Element element) {
        Element a = element.select("a").get(0);
        String href = a.attr("href");

        if (href == null || "".equals(href)) {
            return null;
        }
        if (href.startsWith("./")) {
            href = href.substring(2);
        }
        String url = null;
        if (!href.startsWith("http")) {
            url = "http://www.ccgp-henan.gov.cn/" + href;
        } else {
            url = href;
        }
        try {
            String httpBody = Download.getHttpBody(3, url);
            fujian = getAnnex(Jsoup.parse(httpBody));
            String urlMatched = Util.match("get\\(\"(.*)\",", httpBody)[1];
            if (!urlMatched.startsWith("/")) {
                url = "http://www.ccgp-henan.gov.cn/" + urlMatched;
            } else {
                url = "http://www.ccgp-henan.gov.cn" + urlMatched;
            }
        } catch (Exception ignore) {
        }
        return url;
    }

    @Override
    protected String getAnnex(Document parse) {
        List<String> pdfList = new ArrayList<String>();
        try {
            Elements elements = parse.select(fjxxurlRelu);
            for (int i = 0; i < elements.size(); i++) {
                String href = elements.get(i).attr("href");
                if (!href.startsWith("http")) {
                    href = "http://www.ccgp-henan.gov.cn/" + href;
                }
                pdfList.add(href);
            }
            return pdfList.toString();
        } catch (Exception ignore) {
        }
        return null;
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        Document document = Jsoup.parse(httpBody);
        String channelCode = Util.match("channelCode=(\\d+)&", url)[1];
        List<StructData> allResult = getAllResult(document, httpBody, channelCode);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = getHttpBody(retryTime, tempUrl);
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

    protected List<StructData> getAllResult(Document parse, String httpBody, String channelCode) {
        List<StructData> allResults = new ArrayList<StructData>();
        Elements cListBid = parse.select(this.nodeListRelu);
        for (Element element : cListBid) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                // 获取链接
                String url = getUrl(element);
                logger.info("url: " + url);
                resultData.setArticleurl(url);

                logger.info("annex: " + fujian);
                resultData.setFjxxurl(fujian);

                String title = getTitle(Jsoup.parse(element.html()));
                logger.info("title: " + title);
                resultData.setTitle(title);
                resultData.setDescription(title);

                int catid = cats.getInteger(channelCode);
                logger.info("catId: " + catid);
                resultData.setCat_id(catid);

                // 获取发布时间
                long addTime = getAddTime(element).getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    allResults.removeAll(allResults);
                    return allResults;
                }
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");
        String purchaser = getAuthor(parse);
        purchaser = purchaser.contains("：")? purchaser.split("：")[1]: purchaser;
        logger.info("purchaser: " + purchaser);
        data.setAuthor(purchaser);
        String price = getPrice(parse);
        price = price.contains("：")? price.split("：")[1]: price;
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
    }
}
