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
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static util.Download.getHttpBody;

public class CCGP_JiangXi extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_JiangXi.class);
    private static String relu = "{'002006007': {'authorRelu': 'tbody tr:eq(1) td:eq(0)', 'priceRelu': 'tbody tr:eq(1) td:eq(4)', 'price_unit': '元'},\n" +
            "      '002006001': {'authorRelu': 'span:containsOwn(采购人信息)', 'priceRelu': 'span:containsOwn(预算金额)'},\n" +
            "      '002006002': {'authorRelu': 'span:containsOwn(采购人信息)', 'priceRelu': ''},\n" +
            "      '002006003': {'authorRelu': '', 'priceRelu': ''},\n" +
            "      '002006004': {'authorRelu': 'span:containsOwn(采购人信息)', 'priceRelu': 'span:containsOwn(中标（成交）金额（元） )', 'price_unit': '元'},\n" +
            "      '002006005': {'authorRelu': 'font:containsOwn(采购人)', 'priceRelu': 'font:containsOwn(元)'},\n" +
            "      '002006006': {'authorRelu': 'span:containsOwn(采购人（甲方）)', 'priceRelu': 'span:containsOwn(合同金额)'}\n" +
            "      }";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        final String[] colcodes = {"002006007", "002006001", "002006002", "002006003", "002006004", "002006005", "002006006"}, urls = new String[colcodes.length];
        String prepost_date = Util.getLastMonth(null, 3);
        for (int i = 0; i < colcodes.length; i++) {
            urls[i] = "http://www.ccgp-jiangxi.gov.cn/jxzfcg/services/JyxxWebservice/getList?response=application/json&pageIndex=1&pageSize=50&area=&prepostDate=".concat(prepost_date).concat("&nxtpostDate=&xxTitle=&categorynum=".concat(colcodes[i]));
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 29;
        nodeListRelu = "div#gengerlist div.ewb-infolist ul li";
        titleRelu = "h1";
        fullcontentRelu = "div[align=center]";
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
            this.baseUrl = url;
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected int getMaxPageSize(Document document) {
        return super.getMaxPageSize(document);
    }

    protected String getNextPageUrl(int currentPage, String url) {
        return url.replaceAll("pageIndex=(\\d+)", "pageIndex=" + (currentPage + 1));
    }

    @Override
    protected int getCatId(Document parse) {
        try {
            return getCatIdByText(parse.text());
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        JSONArray document = JSONObject.parseObject(httpBody).getJSONObject("return").getJSONArray("Table");
        List<StructData> allResult = getAllResult(document, httpBody);
        Iterator<StructData> it = allResult.iterator();
        while (it.hasNext()) {
            StructData data = it.next();
            String tempUrl = data.getArticleurl();
            String pageSource = getHttpBody(retryTime, tempUrl);
            if (pageSource == null || pageSource.length() < 1 || pageSource.contains("404 Not Found")) {
                logger.error("下载失败， 直接返回为空");
                it.remove();
                continue;
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
                    currentPage = Integer.parseInt(Util.match("pageIndex=(\\d+)", url)[1]);
                } catch (Exception ignore) {
                }
                String nextPageUrl = getNextPageUrl(currentPage, url);
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
        logger.info("==================================");
        try {
            String colcode = parse.select("input#hiddencate").get(0).attr("value");
            String title = getTitle(parse);
            logger.info("title: " + title);
            data.setTitle(title);
            String description = title;
            logger.info("description: " + description);
            data.setDescription(description);
            int catId = getCatId(parse);
            logger.info("catId: " + catId);
            data.setCat_id(catId);
            int cityId = cityIdRelu;
            logger.info("cityId: " + cityId);
            data.setCity_id(cityId);
            String author = getAuthor(parse, colcode);
            logger.info("author: " + author);
            data.setAuthor(author);
            String price = getPrice(parse, colcode);
            logger.info("price: " + price);
            data.setPrice(price);
            String detail = getDetail(parse);
            logger.info("detail: " + detail);
            data.setFullcontent(detail);
            String annex = getAnnex(parse);
            logger.info("annex: " + annex);
            data.setFjxxurl(annex);
        } catch (Exception e) {
            logger.error(e.toString());
        }
    }

    protected String getPrice(Document parse, String colcode) {
        String price = null;
        Pattern p = Pattern.compile("[0-9]");
        String this_relu = relus.getJSONObject(colcode).get("priceRelu").toString();
        if (this_relu.length() < 1) {
            return price;
        }
        try {
            price = parse.select(relus.getJSONObject(colcode).get("priceRelu").toString()).get(0).text();
            Matcher m = p.matcher(price);
            if (!m.find()) {
                price = parse.select(relus.getJSONObject(colcode).get("priceRelu").toString()).get(0).parent().nextElementSibling().child(1).text();
            }
            price = price.indexOf("：") != -1? price.split("：")[1]: price;
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return price;
    }

    protected String getAuthor(Document parse, String colcode) {
        String author = null;
        String this_relu = relus.getJSONObject(colcode).get("authorRelu").toString();
        if (this_relu.length() < 1) {
            return author;
        }
        try {
            author = parse.select(relus.getJSONObject(colcode).get("authorRelu").toString()).get(0).text();
            author = author.indexOf("采购人信息") != -1 ? parse.select(relus.getJSONObject(colcode).get("authorRelu").toString()).get(0).parent().nextElementSibling().child(1).text() : author;
            author = author.indexOf("：") != -1 ? author.split("：")[1] : author;
            return author;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getTitle(Document parse) {
        return super.getTitle(parse);
    }

    @Override
    protected String getUrl(Element element) {
        String baseUrl = "http://www.ccgp-jiangxi.gov.cn";
        try {
            return baseUrl + element.select("a").get(0).attr("href");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    protected Date getAddTime(Element element) {
        return super.getAddTime(element);
    }

    @Override
    protected String getDetail(Document parse) {
        String detail = null;
        try {
//            detail = Jsoup.clean(parse.select(fullcontentRelu).html(), "", Whitelist.none(), new Document.OutputSettings().prettyPrint(false));
            detail = parse.select(fullcontentRelu).html();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return detail;
    }

    protected List<StructData> getAllResult(JSONArray parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        for (int i = 0; i < parse.size(); i++) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                // 获取链接
                JSONObject element = (JSONObject) parse.get(i);
                String url = "http://www.ccgp-jiangxi.gov.cn/web/jyxx/002006/" + element.get("categorynum") + "/" + element.get("postdate").toString().replaceAll("-", "") + "/" + element.get("infoid") + ".html";
                logger.info("url: " + url);
                resultData.setArticleurl(url);
                // 获取发布时间
                String hits = (String) element.get("postdate");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                Date date = format.parse(hits);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 可以停止采集了");
                    break;
                }
                format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
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
}
