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
import java.util.HashMap;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_NingBo extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_NingBo.class);
    private static HashMap<String, String> heads = new HashMap<String, String>();
    private static String relu = "{'60': {'authorRelu': 'samp[class*=bookmark-item]',\n" +
            "             'priceRelu': 'table[class*=template-bookmark] tbody tr td:eq(3)',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table', 'price_unit': '元'},\n" +
            "      '11': {'authorRelu': 'strong:containsOwn(采购人名称：) + span',\n" +
            "             'priceRelu': 'span:containsOwn(预算金额(元)：) + samp|tr:has(td:containsOwn(预算金额(元)：)) + tr td:eq(3)',\n" +
            "             'price_unit': '元',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '12': {'authorRelu': 'span:has(span:containsOwn(采购人：)) + span span',\n" +
            "             'priceRelu': '',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '13': {'authorRelu': 'span:containsOwn(采购人：) + samp', 'priceRelu': 'span:has(span:containsOwn(预算金额(元)：)) + samp',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table', 'price_unit': '元'},\n" +
            "      '2': {'authorRelu': 'p:has(span:containsOwn(1.采购人信息)) + p span span',\n" +
            "            'priceRelu': 'span:containsOwn(预算金额（元）：) span',\n" +
            "            'fullcontentRelu': 'div.frame_list01 table', 'price_unit': '元'},\n" +
            "      '4': {'authorRelu': 'p:has(span:containsOwn(1.采购人信息)) + p span span',\n" +
            "            'priceRelu': '',\n" +
            "            'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '21': {'authorRelu': 'td:containsOwn(采购人) + td span',\n" +
            "             'priceRelu': 'td:has(b:containsOwn(中标金额（元/优惠率）：)) + td', 'price_unit': '元',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '3': {'authorRelu': 'td:containsOwn(采购人) + td span',\n" +
            "            'priceRelu': '',\n" +
            "            'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '51': {'authorRelu': 'p:has(span:containsOwn(1.采购人信息)) + p span span',\n" +
            "             'priceRelu': 'table[class*=template-bookmark] tbody tr td:eq(1)',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '53': {'authorRelu': 'strong:has(span:containsOwn(一、 采购人名称：)) + span span',\n" +
            "             'priceRelu': 'strong:has(span:containsOwn(预算总金额：)) + span span',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table', 'price_unit': '元'},\n" +
            "      '54': {'authorRelu': 'strong:has(span:containsOwn(一．采购人名称)) + span',\n" +
            "             'priceRelu': '',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '6': {'authorRelu': 'strong:has(span:containsOwn(采购人名称：)) + span',\n" +
            "            'priceRelu': 'table[class*=form-panel-input-cls] tbody tr:eq(1) td:eq(6)', 'price_unit': '元',\n" +
            "            'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '7': {'authorRelu': 'p:has(span:containsOwn(1.采购人信息)) + p span span',\n" +
            "            'priceRelu': '',\n" +
            "            'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      '99': {'authorRelu': 'strong:containsOwn(采购人名称：) + span|p:containsOwn(采购单位名称:) span',\n" +
            "             'priceRelu': 'p:containsOwn(预算总额（元）:) span',\n" +
            "             'fullcontentRelu': 'div.frame_list01 table', 'price_unit': '元'},\n" +
            "      '1': {'authorRelu': 'span:containsOwn(采购人（甲方）：) + span|p:has(span:containsOwn(1.采购人信息:)) + p span span',\n" +
            "            'priceRelu': 'span:containsOwn(合同金额（元）：) span:eq(1)', 'price_unit': '元',\n" +
            "            'fullcontentRelu': 'div.frame_list01 table'},\n" +
            "      }";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        String url = "http://www.ccgp-ningbo.gov.cn/project/zcyNotice.aspx?noticetype=";
        String generateId_str = getHttpBody(10, url + 2);
        heads.put("Referer", "http://www.ccgp-ningbo.gov.cn/project/zcyNotice.aspx?noticetype=2");
        heads.put("Accept", "*/*");
        String __VIEWSTATE = Util.match("__VIEWSTATE\" value=\"(.*)\"", generateId_str)[1];
        final String[] query_signs = {"zcyNotice.aspx?noticetype=60", "zcyNotice.aspx?noticetype=11", "zcyNotice.aspx?noticetype=12", "zcyNotice.aspx?noticetype=13", "zcyNotice.aspx?noticetype=2", "zcyNotice.aspx?noticetype=4", "zcyNotice.aspx?noticetype=21", "zcyNotice.aspx?noticetype=3", "zcyNotice.aspx?noticetype=51", "zcyNotice.aspx?noticetype=53", "zcyNotice.aspx?noticetype=54", "zcyNotice.aspx?noticetype=6", "zcyNotice.aspx?noticetype=7", "zcyNotice.aspx?noticetype=99", "zcyNotice.aspx?noticePeriod=1"}, urls = new String[query_signs.length];
        for (int i = 0; i < query_signs.length; i++) {
            urls[i] = "http://www.ccgp-ningbo.gov.cn/project/".concat(query_signs[i].concat("&#44__VIEWSTATE=" + __VIEWSTATE + "&__EVENTARGUMENT=1&__EVENTTARGET=gdvNotice3$ctl18$AspNetPager1&gdvNotice3$ctl18$AspNetPager1_input=1"));
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 27;
        nodeListRelu = "table[rules=all] tbody tr:has(td:gt(2))";
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
            this.baseUrl = url.split("zcyNotice")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("EVENTARGUMENT=(\\d+)", "EVENTARGUMENT=" + (currentPage + 1)).replaceAll("gdvNotice3\\$ctl18\\$AspNetPager1_input=(\\d+)", "gdvNotice3\\$ctl18\\$AspNetPager1_input="+currentPage);
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url, heads);
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
                String colcode = Util.match("noticetype=(\\d+)", url)[1];
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
    protected void extract(Document parse, StructData data, String colcode) {
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

    protected String getDetail(Document parse, String query_sign) {
        String detail_str = "";
        String[] priceRelus = relus.getJSONObject(query_sign).getString("fullcontentRelu").split("\\|");
        try {
            for (String priceRelu : priceRelus) {
                try {
                    detail_str = parse.select(priceRelu).html();
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
    protected String getUrl(Element element) {
        Element a = null;
        String href = null;
        try {
            a = element.select("td:eq(2) a").get(0);
            href = a.attr("href");
        } catch (Exception e) {
            return "";
        }
        href = this.baseUrl + href;
        return href;
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        Elements cListBid = parse.select(this.nodeListRelu);
        for (Element element : cListBid) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                String hits = element.select("td:eq(3)").get(0).text();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                Date date = format.parse(hits);
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
                String title = element.select("td:eq(2) a").get(0).text();
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
