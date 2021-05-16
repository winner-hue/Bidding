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
import java.util.Iterator;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_ZheJiang extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_ZheJiang.class);
    private static String relu = "{'10016': {'authorRelu': 'samp[class*=code-singleChoicePurchaser]', 'priceRelu': 'table tbody tr td:eq(3)',\n" +
            "                'price_unit': '元'},\n" +
            "      '3012,1002,1003': {'authorRelu': 'span:containsOwn(采购人：) samp', 'priceRelu': 'span:containsOwn(的预算总金额（元）：) samp',\n" +
            "                         'price_unit': '元'},\n" +
            "      '3014': {'authorRelu': '', 'priceRelu': ''},\n" +
            "      '3013': {'authorRelu': 'span:containsOwn(采购人名称：) samp', 'priceRelu': 'span:containsOwn(预算金额(元)：) + samp',\n" +
            "               'price_unit': '元'},\n" +
            "      '3009,4004,3008,2001': {\n" +
            "          'authorRelu': 'strong:has(span:containsOwn(采购人名称：)) + span span|p:has(span:containsOwn(.采购人信息)) + p span samp',\n" +
            "          'priceRelu': 'span:containsOwn(预算金额(元)：) + samp',\n" +
            "          'price_unit': '元'},\n" +
            "      '3001,3020': {'authorRelu': 'p:has(span:containsOwn(.采购人信息)) + p span span',\n" +
            "                    'priceRelu': 'span:containsOwn(预算金额（元）：) span',\n" +
            "                    'price_unit': '元'},\n" +
            "      '3003,3002,3011': {'authorRelu': 'p:has(span:containsOwn(.采购人信息)) + p span span',\n" +
            "                         'priceRelu': 'span:containsOwn(预算金额（元）：) span',\n" +
            "                         'price_unit': '元'},\n" +
            "      '3017,3018,3005,3006,3015': {'authorRelu': 'p:has(span:containsOwn(.采购人信息)) + p span span',\n" +
            "                                   'priceRelu': 'span:containsOwn(预算金额（元）：) span',\n" +
            "                                   'price_unit': '元'},\n" +
            "      '3004,4005,4006': {'authorRelu': 'p:has(span:containsOwn(.采购人信息)) + p span span',\n" +
            "                         'priceRelu': 'td:containsOwn(最终报价:)'},\n" +
            "      '3007,3015': {'authorRelu': 'strong:has(span:containsOwn(采购人名称：)) + span span',\n" +
            "                    'priceRelu': 'strong:has(span:containsOwn(预算总金额：)) + span span',\n" +
            "                    'price_unit': '元'},\n" +
            "      '3010': {'authorRelu': 'span:containsOwn(采购人（甲方）：) span', 'priceRelu': 'span:containsOwn(合同金额（元）：) + span',\n" +
            "               'price_unit': '元'},\n" +
            "      '3016,6003': {'authorRelu': 'span:containsOwn(采购人（甲方）：) + span', 'priceRelu': 'span:containsOwn(合同金额（元）：)',\n" +
            "                    'price_unit': '元'},\n" +
            "      '4002,4001,4003,8006': {'authorRelu': 'strong:has(span:containsOwn(采购人名称：)) + span|span:containsOwn(采购人：)',\n" +
            "                              'priceRelu': 'span:containsOwn(合同金额（元）：)',\n" +
            "                              'price_unit': '元'},\n" +
            "      '1995,1996,1997,8008,8009,8013,8014,9002,9003,808030100': {\n" +
            "          'authorRelu': 'strong:containsOwn(采购人名称：) + span|p:containsOwn(采购单位名称:) span',\n" +
            "          'priceRelu': 'table tbody tr:eq(1) td:eq(6)|p:containsOwn(总成交金额（元）:) span',\n" +
            "          'price_unit': '元'},\n" +
            "      '10001,10002,10012,10003,10014,10004,10013': {'authorRelu': 'span:containsOwn(采购人名称：)',\n" +
            "                                                    'priceRelu': 'span:containsOwn(合同金额（元）：)', 'price_unit': '元'},\n" +
            "      '10006,10007,10008,10009,10010,10011': {\n" +
            "          'authorRelu': 'span:has(span:containsOwn(.采购单位：)) + span span span|span:has(span:containsOwn(采购单位名称))',\n" +
            "          'priceRelu': 'span:containsOwn(合同金额（元）：)', 'price_unit': '元'}}";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        String url = "https://zfcgmanager.czt.zj.gov.cn/cms/api/cors/remote/results?pageSize=15&pageNo=1&url=notice";
        final String[] sourceAnnouncementTypes = {"10016", "3012,1002,1003", "3014", "3013", "3009,4004,3008,2001", "3001,3020", "3003,3002,3011", "3017,3018,3005,3006,3015", "3004,4005,4006", "3007,3015", "3010", "3016,6003", "4002,4001,4003,8006", "1995,1996,1997,8008,8009,8013,8014,9002,9003,808030100", "10001,10002,10012,10003,10014,10004,10013", "10006,10007,10008,10009,10010,10011"}, urls = new String[sourceAnnouncementTypes.length];
        for (int i = 0; i < sourceAnnouncementTypes.length; i++) {
            urls[i] = url.concat("&sourceAnnouncementType=".concat(sourceAnnouncementTypes[i]));
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 27;
        fullcontentRelu = "div#template-center-mark";
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
            this.baseUrl = url.split("/cms/")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String next_url = url.replaceAll("pageNo=(\\d+)", "pageNo=" + (currentPage + 1));
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
                pageSource = JSONObject.parseObject(pageSource).getString("noticeContent");
                Document parse = Jsoup.parse(pageSource);
                String colcode = Util.match("sourceAnnouncementType=(.*)", url)[1];
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

    @Override
    protected void extract(Document parse, StructData data, String colcode) {
        logger.info("==================================");
        String price = getPrice(parse, colcode);
        logger.info("price: " + price);
        data.setPrice(price);
        String author = getAuthor(parse, colcode);
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

    @Override
    protected String getDetail(Document parse) {
        try {
            return parse.select(this.fullcontentRelu).get(0).outerHtml();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONArray rows = JSONObject.parseObject(httpBody).getJSONArray("articles");
            for (int i = 0; i < rows.size(); i++) {
                logger.info("===========================================");
                JSONObject jo = rows.getJSONObject(i);
                StructData resultData = new StructData();
                try {
                    try {
                        Long addTime = jo.getLong("pubDate");
                        logger.info("addTime: " + addTime);
                        if (addTime - this.deadDate.getTime() < 0) {
                            logger.info("发布时间早于截止时间， 不添加该任务url");
                            return allResults;
                        }
                        SimpleDateFormat formats = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        String add_time_name = formats.format(addTime);
                        resultData.setAdd_time(addTime);
                        resultData.setAdd_time_name(add_time_name);
                    } catch (Exception ignore) {
                    }
                    String id = jo.getString("id");
                    String url = "https://zfcgmanager.czt.zj.gov.cn/cms/api/cors/remote/results?url=noticeDetail&noticeId=" + id;
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                } catch (Exception e) {
                    continue;
                }
                int catIdByText = -1;
                try {
                    String catId = jo.getString("typeName");
                    catIdByText = getCatIdByText(catId);
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                resultData.setCity_id(this.cityIdRelu);
                try {
                    String title = jo.getString("title");
                    logger.info("title: " + title);
                    resultData.setTitle(title);
                    resultData.setDescription(title);
                } catch (Exception ignore) {
                }
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("获取json失败：" + e, e);
        }
        return allResults;
    }
}
