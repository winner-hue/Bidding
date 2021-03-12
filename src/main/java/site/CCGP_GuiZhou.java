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
import util.SqlPool;
import util.Util;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static util.Download.getHttpBody;

public class CCGP_GuiZhou extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_GuiZhou.class);
    private static String relu = "{'cgxqgs': {'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "                 'priceRelu': 'span:containsOwn(采购预算：) + span'},\n" +
            "      'cggg': {\n" +
            "          'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "          'priceRelu': 'span:containsOwn(预算金额:) + span'},\n" +
            "      'gzgg': {'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "               'priceRelu': ''},\n" +
            "      'fbgg': {'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "               'priceRelu': ''},\n" +
            "      'zbcggg': {\n" +
            "          'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "          'priceRelu': 'tbody tr:eq(1) td:eq(4)|tbody tr.bidm td:eq(4)'},\n" +
            "      'dylygs': {\n" +
            "          'authorRelu': 'span:containsOwn(采购人:) + span',\n" +
            "          'priceRelu': 'span:containsOwn(预审金额:) + span'},\n" +
            "      'dylycggg': {\n" +
            "          'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "          'priceRelu': 'span:containsOwn(预算金额:) + span'},\n" +
            "      'zgys_1': {\n" +
            "          'authorRelu': 'li:has(span:containsOwn(采购人名称:))',\n" +
            "          'priceRelu': 'li:has(span:containsOwn(采购预算:))'}\n" +
            "      }";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        final String[] urls = {"http://www.ccgp-guizhou.gov.cn/sjbx/cgxqgs/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/cggg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/gzgg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/fbgg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/zbcggg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/dylygs/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/dylycggg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/zgys_1/index.html"};
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 24;
        nodeListRelu = "div.xnrx ul li";
        titleRelu = "h3";
        fullcontentRelu = "div[style=xnrx]";
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

    protected String getNextPageUrl(int currentPage, String url) {
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
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        Document document = Jsoup.parse(httpBody);
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
                    currentPage = Integer.parseInt(Util.match("index_(\\d+)", url)[1]);
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
        SimpleDateFormat format = new SimpleDateFormat("yyyyMM");
        String url = data.getArticleurl(), end_str = format.format(data.getAdd_time() * 1000);
        String colcode = url.substring(url.indexOf("sjbx/") + 5, url.indexOf(end_str) - 1);
        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        String author = getAuthor(parse, colcode);
        logger.info("author: " + author);
        data.setAuthor(author);
        String price = getPrice(parse, colcode);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse, colcode);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    @Override
    protected String getAnnex(Document parse) {
        return super.getAnnex(parse);
    }

    protected String getDetail(Document parse, String query_sign) {
        String detail_str = "";
        try {
            detail_str = parse.select(fullcontentRelu).html();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return detail_str;
    }

    protected String getPrice(Document parse, String query_sign) {
        String price_str = "";
        String[] priceRelus = relus.getJSONObject(query_sign).getString("priceRelu").split("\\|");
        try {
            for (String priceRelu : priceRelus) {
                try {
                    price_str = parse.select(priceRelu).get(0).text();
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (price_str.length() > 0) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return price_str;
    }

    protected String getAuthor(Document parse, String query_sign) {
        String author = "";
        String[] authorRelus = relus.getJSONObject(query_sign).getString("authorRelu").split("\\|");
        try {
            for (String authorRelu : authorRelus) {
                try {
                    author = parse.select(authorRelu).get(0).text();
                } catch (Exception e) {
                    logger.error(e.toString());
                }
                if (author.length() > 0) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return author;
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
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
                String hits = element.select("span").get(0).text();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                Date date = format.parse(hits);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    break;
                }
                String title = element.select("a").get(0).text();
                resultData.setTitle(title);
                resultData.setDescription(title);
                format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                resultData.setCity_id(this.cityIdRelu);
                int catIdByText = -1;
                try {
                    catIdByText = getCatIdByText(title);
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
