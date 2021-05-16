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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_ChinaBidding extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_ChinaBidding.class);
    private static String relu = "{'0105': {\n" +
            "    'authorRelu': 'p:containsOwn(1.采购人) + p|p:containsOwn(招标人：)|p:containsOwn(招标人:)|p:containsOwn(1.采购人信息) + p',\n" +
            "    'priceRelu': 'p:containsOwn(预算金额：)|p:containsOwn(本次招标控制价：)|p:containsOwn(预算金额：)'},\n" +
            "    '0106': {'authorRelu': 'p:containsOwn(1.采购人) + p|span:containsOwn(1.采购人信息) span p:eq(0) span:eq(1)',\n" +
            "             'priceRelu': 'p:has(span:containsOwn(中标金额：)) span:eq(2)'},\n" +
            "    '0107': {'authorRelu': 'span:containsOwn(招标人：)',\n" +
            "             'priceRelu': ''},\n" +
            "    '0108': {\n" +
            "        'authorRelu': 'p:has(span:containsOwn(1.采购人信息)) p span:eq(1)|span:containsOwn(采购人名称：) + span span|td:has(div:containsOwn(招标人)) + td div',\n" +
            "        'priceRelu': 'span:containsOwn(预算金额：) + span|span:containsOwn(预算总金额：) + span span'}\n" +
            "}";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        final String[] infoClassCodes = {"0105", "0106", "0107", "0108"}, urls = new String[infoClassCodes.length];
        for (int i = 0; i < infoClassCodes.length; i++) {
            urls[i] = "https://www.chinabidding.com/search/proj.htm".concat("&#44infoClassCodes=" + infoClassCodes[i] + "&currentPage=1");
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 9;
        nodeListRelu = "ul.as-pager-body li";
        fullcontentRelu = "div[class*=as-article-body]";
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
            logger.info("当前开始url：" + url);
            this.baseUrl = url.split("/search")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("currentPage=(\\d+)", "currentPage=" + (currentPage + 1));
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
                String colcode = Util.match("infoClassCodes=(.*)&", url)[1];
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
                    currentPage = Integer.parseInt(Util.match("currentPage=(\\d+)", url)[1]);
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
        String price = getPrice(parse, colcode);
        logger.info("price: " + price);
        data.setPrice(price);
        String author = getAuthor(parse, colcode);
        logger.info("author: " + author);
        data.setAuthor(author);
        String detail = getDetail(parse);
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
        return price_str;
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
            a = element.select("a").get(0);
            href = a.attr("href");
        } catch (Exception e) {
            return "";
        }
        return href;
    }

    @Override
    protected Date getAddTime(Element element) {
        return super.getAddTime(element);
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        ResultSet rs = null;
        Statement stmt = SqlPool.getInstance().getStatement();
        List<StructData> allResults = new ArrayList<StructData>();
        Elements cListBid = parse.select(this.nodeListRelu);
        for (Element element : cListBid) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                String hits = element.select("h5 span:eq(2)").get(0).text();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                Date date = format.parse(hits.split("：")[1]);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    return allResults;
                }
                // 获取链接
                String url = getUrl(element);
                if (url.equals("") || url == null){
                    continue;
                }
                logger.info("url: " + url);
                resultData.setArticleurl(url);
                String title = element.select("h5 span:eq(1)").get(0).text();
                resultData.setTitle(title);
                resultData.setDescription(title);
                format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                String city_name = element.select("span:containsOwn(所属地区：) strong").get(0).text();
                if (city_name == null) {
                    resultData.setCity_id(4);
                } else {
                    String sql = null;
                    int city_id = 4;
                    if (city_name.contains("市") && !city_name.contains("省")) {
                        city_name = city_name.replace("市", "");
                        sql = "SELECT city_id FROM dizhi where city='" + city_name + "'";
                    } else {
                        city_name = city_name.replace("省", "").replace("回族自治区", "").replace("壮族自治区", "").replace("维吾尔自治区", "").replace("自治区", "");
                        sql = "SELECT city_id FROM dizhi where city_name='" + city_name + "'";
                    }
                    try {
                        rs = stmt.executeQuery(sql);
                        while(rs.next()) {
                            city_id = rs.getInt(1);
                            break;
                        }
                    } catch (SQLException throwables) {
                        throwables.printStackTrace();
                    }
                    resultData.setCity_id(city_id);
                }
                int catIdByText = -1;
                try {
                    catIdByText = getCatIdByText(element.select("h5 span:eq(0)").get(0).text());
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
