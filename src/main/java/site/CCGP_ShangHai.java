package site;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.SqlPool;
import util.Util;

import javax.print.Doc;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static util.Download.getHttpBody;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*上海*/

public class CCGP_ShangHai extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_ShangHai.class);

    @Override
    public void run() {
        setValue();
        int length_num = 6;
        String url_start = "http://www.zfcg.sh.gov.cn/front/search/category";
        String[] urls = new String[length_num];
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String startDate = format.format(new Date(System.currentTimeMillis() - 3 * 30 * 24 * 60 * 60 * 1000L));
        for (int i = 0; i < length_num; i++) {
            String code = "ZcyAnnouncement".concat(String.valueOf(i + 1));
            urls[i] = url_start.concat("&#44utm=sites_group_front.2ef5001f.0.0.f5992140395c11eb9a363987d6b81c96&categoryCode=".concat(code).concat("&pageSize=50&pageNo=1&publishDateBegin=".concat(startDate).concat("&application_jsons")));
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    protected int getMaxPageSize() {
        return this.maxPageSize;
    }

    protected String getNextPageUrl(int currentPage, String url) {
        String nextPageUrl = null;
        if (this.maxPageSize == 1) {
            this.maxPageSize = getMaxPageSize();
            logger.info("maxPageSize: " + this.maxPageSize);
        }
        if (((currentPage == maxPageSize) && (maxPageSize != 0)) || currentPage < this.maxPageSize) {
            nextPageUrl = url.replaceAll("pageNo=(\\d+)", "pageNo=" + (currentPage + 1));
            logger.info("nextPageUrl: " + nextPageUrl);
        }
        return nextPageUrl;
    }

    protected String getTitle(JSONObject parse) {
        return parse.getString("title");
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        String total = Util.match("total\":(\\d+),\"max", httpBody)[1];
        this.maxPageSize = (int) Math.ceil(Double.parseDouble(total) / 50);
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = getHttpBody(retryTime, tempUrl);
            Document parse = Jsoup.parse(pageSource);
            Element detail_ele = parse.select("input[name='articleDetail']").get(0);
            String detail_str = detail_ele.attr("value");
            JSONObject detail_json = JSONObject.parseObject(detail_str);
            if (!detail_json.containsKey("title")) {
                return;
            }
            extractx(detail_json, data, pageSource);
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
                    currentPage = Integer.parseInt(Util.match("pageNo=(\\d+)&", url)[1]);
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

    protected void extractx(JSONObject parse_json, StructData data, String pageSource) {
        logger.info("==================================");
        Document doc = Jsoup.parse(parse_json.getString("content"));
        String title = getTitle(parse_json);
        logger.info("title: " + title);
        data.setTitle(title);
        String description = getDescription(parse_json);
        logger.info("description: " + description);
        data.setDescription(description);
        int catId = getCatIdByText(pageSource);
        logger.info("catId: " + catId);
        data.setCat_id(catId);
        int cityId = 5;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);
        String author = getAuthor(parse_json, doc);
        logger.info("author: " + author);
        data.setAuthor(author);
        String price = getPrice(parse_json, doc);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse_json, doc);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(doc);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
        String add_time_name = data.getAdd_time_name();
        data.setAdd_time_name(add_time_name);
    }

    protected String getDetail(JSONObject parse, Document doc) {
        return doc.outerHtml();
    }

    protected String getPrice(JSONObject parse, Document doc) {
        String price = null, max_price = "0", regex = "(\\d+\\.[0-0]{2})";
        long num = 0;
        String page_str = parse.getString("content");
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher m = pattern.matcher(page_str);
            while(m.find()){
                price = m.group(0);
                num = Double.valueOf(price).intValue();
                if (num > Double.valueOf(max_price).intValue()) {
                    max_price = price;
                }
            }
            return max_price;
        } catch (Exception e) {
            logger.error(String.valueOf(e));
            return "";
        }
    }

    protected String getAuthor(JSONObject parse, Document doc) {
        String author = null;
        String page_str = parse.getString("content");
        try {
            if (page_str.contains("采购单位：")) {
                author = doc.select("span:containsOwn(采购单位：)").get(0).text().split("采购单位：")[1];
            } else if (page_str.contains("采购人：")) {
                Elements els = doc.select("samp[class$=editDisable interval-text-box-cls readonly]");
                if (els.size() > 0) {
                    author = els.get(0).text();
                } else if (doc.select("span:containsOwn(采购人：) ~ span").size() == 0) {
                    author = doc.select("span:containsOwn(采购人：)").get(0).text().split("采购人：")[1];
                } else {
                    author = doc.select("span:containsOwn(采购人：) ~ span").get(0).text();
                }
            } else if (page_str.contains("采购人名称：")) {
                author = doc.select("span:containsOwn(采购人名称：)").get(0).text().split("采购人名称：")[1];
            }
            else if (page_str.contains("采购人信息")) {
                String regex = "editDisable interval-text-box-cls readonly\" style=\"font-family: inherit;\">(.*?)<";
                try {
                    Pattern pattern = Pattern.compile(regex);
                    Matcher m = pattern.matcher(parse.getString("content"));
                    while(m.find()){
                        return m.group(1);
                    }
                    return "";
                } catch (Exception e) {
                    return "";
                }
            } else {
                author = doc.select("span:containsOwn(采购人（甲方）)").get(0).children().get(0).text();
            }
            return author;
        } catch (Exception e) {
            logger.error(String.valueOf(e));
            return "";
        }
    }

    protected String getDescription(JSONObject parse) {
        return parse.getString("title");
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONArray rows = JSONObject.parseObject(httpBody).getJSONObject("hits").getJSONArray("hits");
            for (int i = 0; i < rows.size(); i++) {
                logger.info("===========================================");
                JSONObject jo = rows.getJSONObject(i);
                StructData resultData = new StructData();
                try {
                    String source_url = jo.getJSONObject("_source").getString("url");
                    String url = "http://www.zfcg.sh.gov.cn" + source_url;
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                } catch (Exception e) {
                    continue;
                }
                try {
                    long newworkDateAll = (Long) jo.getJSONArray("sort").get(0);
                    logger.info("addTime: " + newworkDateAll);
                    if (newworkDateAll - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        break;
                    }
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String add_time_name = format.format(newworkDateAll);
                    resultData.setAdd_time(newworkDateAll);
                    resultData.setAdd_time_name(add_time_name);
                } catch (Exception ignore) {
                    logger.error(ignore.toString());
                }
                int catIdByText = -1;
                try {
                    String catId = jo.getJSONObject("_source").getString("pathName");
                    catIdByText = getCatIdByText(catId);
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                logger.info("cityId: " + this.cityIdRelu);
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("获取json失败：" + e, e);
        }
        return allResults;
    }
}
