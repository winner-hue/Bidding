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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_DaLian extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_DaLian.class);
    private static String relu = "{'003001001': {'authorRelu': 'tr:has(td:containsOwn(1.采购人信息)) + tr td:eq(1) p span:eq(1)|tr:has(td:containsOwn(1.采购人信息)) + tr td:eq(1)',\n" +
            "                    'priceRelu': 'span:containsOwn(预算金额：) + span',\n" +
            "                    'price_unit': '万元'},\n" +
            "      '003002001': {'authorRelu': 'tr:has(td:containsOwn(1.采购人信息)) + tr td:eq(1) p span:eq(1)',\n" +
            "                    'priceRelu': 'table#_Sheet1_8_0 tbody tr:eq(2) td:eq(4)'},\n" +
            "      '003004001': {'authorRelu': 'span:containsOwn(采购人：) + span',\n" +
            "                    'priceRelu': ''},\n" +
            "      '003005001': {'authorRelu': 'span:containsOwn(采购人（甲方）：) + span',\n" +
            "                    'priceRelu': 'span:containsOwn(合同金额：) + span'},\n" +
            "      '003006001': {'authorRelu': 'span:containsOwn(规定，现将) + span',\n" +
            "                    'priceRelu': 'table#_Sheet1_7_0 tbody tr:eq(2) td:eq(3)',\n" +
            "                    'price_unit': '万元'},\n" +
            "      '003001002': {'authorRelu': 'tr:has(td:containsOwn(1.采购人信息)) + tr td:eq(1) p span:eq(1)|tr:has(td:containsOwn(1.采购人信息)) + tr td:eq(1)',\n" +
            "                    'priceRelu': 'span:containsOwn(预算金额：) + span',\n" +
            "                    'price_unit': '万元'},\n" +
            "      '003002002': {'authorRelu': 'tr:has(td:containsOwn(1.采购人信息)) + tr td:eq(1) p span:eq(1)',\n" +
            "                    'priceRelu': 'table#_Sheet1_8_0 tbody tr:eq(2) td:eq(4)'},\n" +
            "      '003004002': {'authorRelu': 'span:containsOwn(采购人：) + span',\n" +
            "                    'priceRelu': ''},\n" +
            "      '003005002': {'authorRelu': 'span:containsOwn(采购人（甲方）：) + span',\n" +
            "                    'priceRelu': 'span:containsOwn(合同金额：) + span'},\n" +
            "      '003006002': {'authorRelu': 'span:containsOwn(规定，现将) + span',\n" +
            "                    'priceRelu': 'table#_Sheet1_7_0 tbody tr:eq(2) td:eq(3)',\n" +
            "                    'price_unit': '万元'}\n" +
            "      }";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        String url = "http://www.ccgp-dalian.gov.cn/dlweb/showinfo/bxmoreinfo.aspx?CategoryNum=";
        final String[] categoryNumS = {"003001001", "003002001", "003004001", "003005001", "003006001", "003001002", "003002002", "003004002", "003005002", "003006002"}, urls = new String[categoryNumS.length];
        for (int i = 0; i < categoryNumS.length; i++) {
            urls[i] = url.concat(categoryNumS[i]).concat("&#44__EVENTTARGET=MoreInfoList$Pager&__EVENTARGUMENT=1");
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 17;
        nodeListRelu = "table#MoreInfoList_DataGrid1 tbody tr";
        fullcontentRelu = "table#tblInfo";
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
            this.baseUrl = url.split("/dlweb")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("__EVENTARGUMENT=(\\d+)", "__EVENTARGUMENT=" + (currentPage + 1));
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
                extract(parse, data, pageSource);
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
                    currentPage = Integer.parseInt(Util.match("EVENTARGUMENT=(\\d+)", url)[1]);
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
    protected void extract(Document parse, StructData data, String pageSource) {
        String url = data.getArticleurl();
        String colcode = url.substring(url.indexOf("CategoryNum=") + 12);
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
                    if (relus.getJSONObject(query_sign).containsKey("price_unit")) {
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
                String hits = element.select("td:eq(2)").get(0).text();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                Date date = format.parse(hits);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    break;
                }
                String title = element.select("td:eq(1) a").get(0).text();
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
