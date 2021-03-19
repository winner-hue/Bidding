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
import util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CCGP_ChongQing extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_ChongQing.class);

    @Override
    protected void setValue() {
        titleRelu = "h2#titlecandel";
        // 采集类型id规则
        catIdRelu = "h2#titlecandel";
        // 城市id规则
        cityIdRelu = 21;
        // 采购人规则
        authorRelu = "p:matchesOwn(采购人：)";
        // 价格规则
        priceRelu = "p:matchesOwn(成交金额：)";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd HH:mm:ss";
        // 内容规则
        fullcontentRelu = "div.wrap-post h4,p";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.chongqing.url").split(",");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String endDate = format.format(new Date(System.currentTimeMillis()));
        String startDate = format.format(new Date(System.currentTimeMillis() - 3 * 30 * 24 * 60 * 60 * 1000L));
        String[] newUrls = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i].replaceAll("DateLine", endDate).replaceAll("DateStart", startDate);
            newUrls[i] = url;
        }
        this.main(newUrls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        try {
            logger.info("==================================");
            JSONObject jo = JSON.parseObject(pageSource).getJSONObject("notice");
            String title = null;
            try {
                title = jo.getString("title");
            } catch (Exception ignore) {
            }
            logger.info("title: " + title);
            data.setTitle(title);
            int catId = -1;
            try {
                catId = getCatIdByText(jo.getString("projectPurchaseWayName"));
            } catch (Exception ignore) {
            }
            logger.info("catId: " + catId);
            data.setCat_id(catId);
            int cityId = cityIdRelu;
            logger.info("cityId: " + cityId);
            data.setCity_id(cityId);
            String purchaser = null;
            try {
                purchaser = jo.getString("buyerName");
            } catch (Exception ignore) {
            }
            logger.info("purchaser: " + purchaser);
            data.setAuthor(purchaser);
            String price = null;
            try {
                price = getPrice(Jsoup.parse(jo.getString("html")));
            } catch (Exception ignore) {
            }
            logger.info("price: " + price);
            data.setPrice(price);
            String detail = null;
            try {
                detail = Jsoup.parse(jo.getString("html")).html();
            } catch (Exception ignore) {
            }
            logger.info("detail: " + detail);
            data.setFullcontent(detail);
            List<String> fileList = new ArrayList<String>();
            try {
                JSONArray attachments = jo.getJSONArray("attachments");
                for (int i = 0; i < attachments.size(); i++) {
                    String value = attachments.getJSONObject(i).getString("value");
                    if (!value.startsWith("http")) {
                        value = "https://www.ccgp-chongqing.gov.cn/" + value;
                    }
                    fileList.add(value);
                }
            } catch (Exception ignore) {
            }
            logger.info("fjxxurl: " + fileList.toString());
            if (fileList.size() > 0) {
                data.setFjxxurl(fileList.toString());
            } else {
                data.setFjxxurl(null);
            }
            String add_time_name = data.getAdd_time_name();
            data.setAdd_time_name(add_time_name);
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
            JSONArray notices = JSON.parseObject(httpBody).getJSONArray("notices");
            for (int i = 0; i < notices.size(); i++) {
                JSONObject jo = notices.getJSONObject(i);
                logger.info("===========================================");
                StructData resultData = new StructData();
                // 获取链接
                try {
                    String id = jo.getString("id");
                    String url = "https://www.ccgp-chongqing.gov.cn/gwebsite/api/v1/notices/stable/" + id;
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                    // 获取链接md5值， 用于排重
                    String md5 = Util.stringToMD5(url);
                    logger.info("md5: " + md5);
                    //resultData.setMd5(md5);
                } catch (Exception ignore) {
                    continue;
                }
                // 获取发布时间
                try {
                    Long issueTime = jo.getLong("issueTime");
                    logger.info("addTime: " + issueTime);
                    if (issueTime - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        continue;
                    }
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String add_time_name = format.format(issueTime);
                    resultData.setAdd_time(issueTime);
                    resultData.setAdd_time_name(add_time_name);
                } catch (Exception e) {
                    logger.error("获取时间错误：" + e, e);
                    continue;
                }
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("提取连接错误：" + e, e);
        }
        return allResults;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("pi=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("pi=\\d+", "pi=" + (Integer.parseInt(id) + 1));
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }
}