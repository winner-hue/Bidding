package site;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

public class CCGP_GuangXi extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_GuangXi.class);
    private static String relu = "{'ZcyAnnouncement1': {\n" +
            "    'priceRelu': 'span:containsOwn(预算金额（人民币）：)|span:has(span:containsOwn(预算金额：))|span:containsOwn(预算金额：)'},\n" +
            "    'ZcyAnnouncement2': {\n" +
            "        'priceRelu': 'span:containsOwn(中标（成交）金额：) + span'},\n" +
            "    'ZcyAnnouncement3': {\n" +
            "        'priceRelu': 'span:containsOwn(合同金额（元）：) span:eq(1)',\n" +
            "        'price_unit': '元'},\n" +
            "\n" +
            "    'ZcyAnnouncement4': {\n" +
            "        'priceRelu': 'span:containsOwn(预算总造价为) + strong span'},\n" +
            "\n" +
            "    'ZcyAnnouncement5': {\n" +
            "        'priceRelu': 'tbody tr td:eq(4)'},\n" +
            "    'ZcyAnnouncement6': {\n" +
            "        'priceRelu': 'span:has(span:has(span:containsOwn(预算金额(元)：))) + samp|span:containsOwn(预算金额：)',\n" +
            "        'price_unit': '元'},\n" +
            "    'ZcyAnnouncement7': {\n" +
            "        'priceRelu': 'td:containsOwn(成交总价) + td|td:containsOwn(项目预算) + td'},\n" +
            "    'ZcyAnnouncement9': {\n" +
            "        'priceRelu': 'td:containsOwn(成交总价) + td|td:containsOwn(项目预算) + td'}\n" +
            "}";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        String url = "http://www.ccgp-guangxi.gov.cn/front/search/category";
        final String[] categoryCodes = {"ZcyAnnouncement1", "ZcyAnnouncement2", "ZcyAnnouncement3", "ZcyAnnouncement4", "ZcyAnnouncement5", "ZcyAnnouncement6", "ZcyAnnouncement7", "ZcyAnnouncement9"}, urls = new String[categoryCodes.length];
        for (int i = 0; i < categoryCodes.length; i++) {
            urls[i] = url.concat("&#44categoryCode=").concat(categoryCodes[i]).concat("&pageSize=15&pageNo=1&application_jsons");
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 16;
        fullcontentRelu = "div#print-content";
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
            this.baseUrl = url.split("/front")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("pageNo=(\\d+)", "pageNo=" + (currentPage + 1));
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
            try {
                pageSource = getHttpBody(retryTime, tempUrl);
                Document parse = Jsoup.parse(pageSource);
                Element detail_ele = parse.select("input[name='articleDetail']").get(0);
                String detail_str = detail_ele.attr("value");
                JSONObject detail_json = JSONObject.parseObject(detail_str);
                if (!detail_json.containsKey("title")) {
                    return;
                }
                extractx(detail_json, data, pageSource);
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
                    currentPage = Integer.parseInt(Util.match("pageNo=(\\d+)", url)[1]);
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

    protected String getPrice(Document parse, String classname) {
        String price_str = "";
        String[] priceRelus = new String[0];
        priceRelus = relus.getJSONObject(classname).getString("priceRelu").split("\\|");
        try {
            for (String priceRelu : priceRelus) {
                try {
                    price_str = parse.select(priceRelu).get(0).text();
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (price_str.length() > 0) {
                    if (relus.getJSONObject(classname).containsKey("price_unit") && !price_str.contains(relus.getJSONObject(classname).getString("price_unit"))) {
                        price_str = price_str + relus.getJSONObject(classname).getString("price_unit");
                    }
                    if (price_str.contains("：") && price_str.split("：").length <= 1) {
                        return "";
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return price_str;
    }

    protected String getDetail(Document parse) {
        return parse.outerHtml();
    }

    protected void extractx(JSONObject parse_json, StructData data, String pageSource) {
        logger.info("==================================");
        String classname = Util.match("/ZcyAnnouncement/(.*)/Zcy", data.getArticleurl())[1];
        Document doc = Jsoup.parse(parse_json.getString("content"));
        String author = parse_json.getString("author");
        logger.info("author: " + author);
        data.setAuthor(author);
        String price = getPrice(doc, classname);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = parse_json.getString("content").replaceAll("\\'", "\"");
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(doc);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        JSONArray rows = JSONObject.parseObject(httpBody).getJSONObject("hits").getJSONArray("hits");
        for (int i = 0; i < rows.size(); i++) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            JSONObject jo = rows.getJSONObject(i);
            try {
                Long addTime = jo.getJSONObject("_source").getLong("publishDate");
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    return allResults;
                }
                // 获取链接
                String url = this.baseUrl + jo.getJSONObject("_source").getString("url");
                logger.info("url: " + url);
                resultData.setArticleurl(url);
                String title = jo.getJSONObject("_source").getString("title");
                resultData.setTitle(title);
                resultData.setDescription(title);
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                resultData.setCity_id(this.cityIdRelu);
                int catIdByText = -1;
                try {
                    String cst = jo.getJSONObject("_source").getString("procurementMethod");
                    catIdByText = getCatIdByText(cst);
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }
}
