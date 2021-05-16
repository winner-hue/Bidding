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

public class CCGP_JiangSu extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_JiangSu.class);
    private static String relu = "{'cgyx': {'authorRelu': 'p:containsOwn(采购文件为准。) + p',\n" +
            "               'priceRelu': 'div.IntentionDisclosurePreviewTable table tbody tr td:eq(3) div',\n" +
            "               'fullcontentRelu': 'div.IntentionDisclosurePreviewTable'},\n" +
            "      'zgysgg': {'authorRelu': 'span:containsOwn(采购人：) u', 'priceRelu': 'span:containsOwn(的预算金额：) u span',\n" +
            "                 'fullcontentRelu': 'div.detail_con', 'price_unit': '元'},\n" +
            "      'gkzbgg': {'authorRelu': 'p:has(span:containsOwn(.采购人信息)) + p span', 'priceRelu': 'span:containsOwn(预算金额：)',\n" +
            "                 'fullcontentRelu': 'div.detail_con', 'price_unit': ''},\n" +
            "      'yqzbgg': {'authorRelu': 'span:has(span:containsOwn(采购单位：)) + span span',\n" +
            "                 'priceRelu': 'span:has(span:containsOwn(采购预算：)) + span|span:containsOwn(预算金额：) + span',\n" +
            "                 'fullcontentRelu': 'div.detail_con'},\n" +
            "      'jztbgg': {'authorRelu': 'p:has(span:containsOwn(采购人信息)) + p span',\n" +
            "                 'priceRelu': 'span:containsOwn(预算金额：)|span:containsOwn(预算金额为)', 'fullcontentRelu': 'div.detail_con'},\n" +
            "      'jzqsgg': {'authorRelu': 'span:containsOwn(采购人：)|p:has(span:containsOwn(采购人信息：)) + p span',\n" +
            "                 'priceRelu': 'span:containsOwn(预算金额：)|span:containsOwn(预算金额：) + span',\n" +
            "                 'fullcontentRelu': 'div.detail_con', 'price_unit': '万'},\n" +
            "      'dylygg': {'authorRelu': 'p:containsOwn(采购人：)',\n" +
            "                 'priceRelu': 'span:has(span:containsOwn(的预算金额：)) + span span|span:containsOwn(的预算金额：)',\n" +
            "                 'fullcontentRelu': 'div.detail_con', 'price_unit': '万'},\n" +
            "      'xjgg': {'authorRelu': 'p:has(span:has(span:containsOwn(、采购人信息))) + p span:eq(0) span:eq(1)',\n" +
            "               'priceRelu': 'span:containsOwn(采购预算、最高限价：) + span', 'fullcontentRelu': 'div.detail_con'},\n" +
            "      'zbgg': {\n" +
            "          'authorRelu': 'h2:has(span:containsOwn(.采购人信息)) + p span span:eq(1) span|p:has(span:has(span:containsOwn(.采购人信息))) + p span:eq(0) span',\n" +
            "          'priceRelu': 'span:containsOwn(中标（成交）金额：) + span', 'fullcontentRelu': 'div.detail_con'},\n" +
            "      'cgcjgg': {\n" +
            "          'authorRelu': 'p:has(span:has(span:containsOwn(.采购人信息))) + p span:eq(0) span:eq(1)|p:has(span:containsOwn(.采购人信息)) + p span',\n" +
            "          'priceRelu': 'span:has(span:containsOwn(成交金额：)) + span span|span:containsOwn(成交金额：)',\n" +
            "          'fullcontentRelu': 'div.detail_con'},\n" +
            "      'zzgg': {'authorRelu': 'h2:has(span:containsOwn(.采购人信息)) + p a:eq(1) span span', 'priceRelu': '',\n" +
            "               'fullcontentRelu': 'div.detail_con'},\n" +
            "      'cggzgg': {\n" +
            "          'authorRelu': 'p:has(span:containsOwn(采购人信息)) + p span|p:has(span:containsOwn(.采购人信息)) + p span span:eq(1)',\n" +
            "          'priceRelu': 'span:containsOwn(预算金额：)', 'fullcontentRelu': 'div.detail_con'},\n" +
            "      'qtgg': {'authorRelu': 'p:has(span:containsOwn(采购人信息)) + p span|span:has(span:containsOwn(采购单位：)) + span span',\n" +
            "               'priceRelu': '', 'fullcontentRelu': 'div.detail_con'},\n" +
            "      'htgg_1': {'authorRelu': 'td:containsOwn(采购人) + td', 'priceRelu': 'td:containsOwn(合同总金额) + td',\n" +
            "                 'fullcontentRelu': 'div.detail'}}";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        String url = "http://www.ccgp-jiangsu.gov.cn/ggxx/";
        final String[] purchaseManners = {"cgyx", "zgysgg", "gkzbgg", "yqzbgg", "jztbgg", "jzqsgg", "dylygg", "xjgg", "zbgg", "cgcjgg", "zzgg", "cggzgg", "qtgg", "htgg_1"}, urls = new String[purchaseManners.length];
        for (int i = 0; i < purchaseManners.length; i++) {
            urls[i] = url.concat(purchaseManners[i]).concat("/index.html");
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 15;
        fullcontentRelu = "div#print-content";
        nodeListRelu = "div#newsList ul li";
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
//                this.deadDate = format.parse("2021-03-11");
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        for (String url : urls) {
            logger.info("当前开始url： " + url);
            this.baseUrl = url.split("index")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String next_url = null;
        if (url.contains("index_")) {
            next_url = url.replaceAll("index_(\\d+)", "index_" + (currentPage + 1));
        } else {
            next_url = url.replaceAll("index", "index_" + (currentPage + 1));
        }
        return next_url;
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
                String colcode = Util.match("ggxx/(.*)/index", url)[1];
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
    protected void extract(Document parse, StructData data, String colcode) {
        logger.info("==================================");
        String title = parse.select("div.dtit h1").get(0).text();
        data.setTitle(title);
        data.setDescription(title);
        int catIdByText = -1;
        try {
            catIdByText = getCatIdByText(title);
            logger.info("catId: " + catIdByText);
        } catch (Exception ignore) {
        }
        data.setCat_id(catIdByText);
        String price = getPrice(parse, colcode);
        logger.info("price: " + price);
        data.setPrice(price);
        String author = getAuthor(parse, colcode);
        data.setAuthor(author);
        String detail = getDetail(parse, colcode);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    protected String getPrice(Document parse, String query_sign) {
        String price_str = "";
        String[] priceRelus = relus.getJSONObject(query_sign).getString("priceRelu").split("\\|");
        try {
            for (String priceRelu : priceRelus) {
                try {
                    price_str = parse.select(priceRelu).get(0).text();
                    price_str = price_str.contains("：")? price_str.split("：")[1]: price_str;
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (price_str.length() > 0) {
                    if (relus.getJSONObject(query_sign).containsKey("price_unit") && !price_str.contains(relus.getJSONObject(query_sign).getString("price_unit"))) {
                        price_str = price_str + relus.getJSONObject(query_sign).getString("price_unit");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return price_str.trim();
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
        return author.trim();
    }

    protected String getDetail(Document parse, String query_sign) {
        String detail_str = "";
        String[] priceRelus = relus.getJSONObject(query_sign).getString("fullcontentRelu").split("\\|");
        try {
            for (String priceRelu : priceRelus) {
                try {
                    detail_str = parse.select(priceRelu).outerHtml().replaceAll("\\'", "\"");
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (detail_str.length() > 0) {
                    detail_str = detail_str.replaceAll("\\'", "\\\\'");
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return detail_str;
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        Elements cListBid = parse.select(this.nodeListRelu);
        for (Element element : cListBid) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                String hits = element.ownText().trim();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                Date date = format.parse(hits);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    return allResults;
                }
                format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                // 获取链接
                String url = getUrl(element);
                if (url.equals("") || url == null){
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
