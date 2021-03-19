package site;

import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

public class CCGP_QingDao extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_QingDao.class);
    private static HashMap<String, String> heads = new HashMap<String, String>();
    private static String relu = "{'0401': {'authorRelu': 'td:containsOwn(采购人) + td span',\n" +
            "               'priceRelu': 'span:containsOwn(本项目预算金额为)|span:containsOwn(预算金额：)',\n" +
            "               'fullcontentRelu': 'div.cont'},\n" +
            "      '0402': {'authorRelu': 'td:containsOwn(采购人) + td span',\n" +
            "               'priceRelu': 'td:has(b:containsOwn(中标金额（元/优惠率）：)) + td', 'price_unit': '元',\n" +
            "               'fullcontentRelu': 'div.cont'},\n" +
            "      '0403': {'authorRelu': 'td:containsOwn(采购人) + td span',\n" +
            "               'priceRelu': '',\n" +
            "               'fullcontentRelu': 'div.cont'},\n" +
            "      '0404': {'authorRelu': 'td:containsOwn(采购人) + td span', 'priceRelu': '',\n" +
            "               'fullcontentRelu': 'div.cont'},\n" +
            "      '0405': {'authorRelu': 'span:containsOwn(采购人：)|p:containsOwn(采购人：) u', 'priceRelu': 'span:containsOwn(预算金额：)',\n" +
            "               'fullcontentRelu': 'div.cont'},\n" +
            "      '0406': {'authorRelu': 'div:containsOwn(经采购人) + u b', 'priceRelu': 'td:has(b:containsOwn(合计金额)) + td',\n" +
            "               'fullcontentRelu': 'table', 'price_unit': '元'}\n" +
            "      }";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        heads.put("Referer", "http://www.ccgp-qingdao.gov.cn/sdgp2014/site/channelall370200.jsp?colcode=0401&flag=0401");
        heads.put("Content-Type", "text/plain");
        String url = "http://www.ccgp-qingdao.gov.cn/sdgp2014/dwr/call/plaincall/dwrmng.queryWithoutUi.dwr", generateId_url = "http://www.ccgp-qingdao.gov.cn/sdgp2014/dwr/call/plaincall/__System.generateId.dwr&#44callCount=1&c0-scriptName=__System&c0-methodName=generateId&c0-id=0&batchId=0&instanceId=0&page=/sdgp2014/site/channelall370200.jsp?colcode=0401&flag=0401&scriptSessionId=";
        String generateId_str = getHttpBody(5, generateId_url, heads);
        String scriptSessionId = Util.match("0\",\"0\",\"(.*)\"", generateId_str)[1], _pageId = Util.getRandomString(7) + "-" + Util.getRandomString(9);
        final String[] query_signs = {"0401", "0402", "0403", "0404", "0405", "0406"}, urls = new String[query_signs.length];
        for (int i = 0; i < query_signs.length; i++) {
            urls[i] = url.concat("&#44callCount=1&nextReverseAjaxIndex=0&c0-scriptName=dwrmng&c0-methodName=queryWithoutUi&c0-id=0&c0-param0=number:7&c0-e1=string:" + query_signs[i] + "&c0-e2=string:" + (i + 1) + "&c0-e3=number:20&c0-e4=string:&c0-e5=null:null&c0-param1=Object_Object:{_COLCODE:reference:c0-e1, _INDEX:reference:c0-e2, _PAGESIZE:reference:c0-e3, _REGION:reference:c0-e4, _KEYWORD:reference:c0-e5}&batchId=4&instanceId=0&page=%2Fsdgp2014%2Fsite%2Fchannelall370200.jsp%3Fcolcode%3D0401%26flag%3D0401&scriptSessionId=" + scriptSessionId + "/" + _pageId);
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 9;
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
            this.baseUrl = url.split("/sdgp2014")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("c0-e2=string:(\\d+)&", "c0-e2=string:" + (currentPage + 1) + "&");
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url, heads);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        try {
            httpBody = Util.match(",\"0\",(.*)\\)", httpBody)[1];
        } catch (Exception e) {
            logger.error(e.toString());
        }
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = null;
            try {
                logger.info("准备开始请求：" + tempUrl);
                pageSource = getHttpBody(retryTime, tempUrl);
                Document parse = Jsoup.parse(pageSource);
                String colcode = Util.match("c0-e1=string:(.*)&c0-e2", url)[1];
                extract(parse, data, colcode);
                logger.info("解析完成：");
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
                    currentPage = Integer.parseInt(Util.match("c0-e2=string:(\\d+)&", url)[1]);
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

    protected void extract(Document parse, StructData data, String colcode) {
        int cityId = cityIdRelu;
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
        String author = getAuthor(parse, colcode);
        logger.info("author: " + author);
        data.setAuthor(author);
        if ((colcode.equals("0405") || colcode.equals("0406")) == false) {
            String url = null;
            try {
                url = "http://www.ccgp-qingdao.gov.cn/sdgp2014/site/noticeInfo" + Util.match("url1 = \"noticeInfo(.*)\"", parse.html())[1];
                String httpBody = getHttpBody(10, url);
                if (httpBody == null) {
                    logger.error("下载失败， 直接返回为空");
                    return;
                } else {
                    data.setArticleurl(url);
                    parse = Jsoup.parse(httpBody);
                }
            } catch (Exception e) {
                logger.error(e.toString());
            }

        }
        logger.info("cityId: " + cityId);
        String price = getPrice(parse, colcode);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse, colcode);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
    }

    @Override
    protected String getAnnex(Document parse) {
        return super.getAnnex(parse);
    }

    protected String getDetail(Document parse, String query_sign) {
        String detail_str = "";
        String[] priceRelus = relus.getJSONObject(query_sign).getString("fullcontentRelu").split("\\|");
        try {
            for (String priceRelu : priceRelus) {
                try {
                    detail_str = parse.select(priceRelu).outerHtml();
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
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONObject rows = JSONObject.parseObject(httpBody);
            String[] subject = rows.getString("rsltStringValue").split("!")[0].split("\\?");
            for (int i = 0; i < subject.length; i++) {
                logger.info("===========================================");
                String[] single = subject[i].split(",");
                StructData resultData = new StructData();
                try {
                    String url = "http://www.ccgp-qingdao.gov.cn/sdgp2014/site/read" + single[3] + ".jsp?id=" + single[0] + "&flag=0401";
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                } catch (Exception e) {
                    continue;
                }
                try {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    Date date = format.parse(single[2]);
                    long newworkDateAll = date.getTime();
                    logger.info("addTime: " + newworkDateAll);
                    if (newworkDateAll - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        break;
                    }
                    format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String add_time_name = format.format(newworkDateAll);
                    resultData.setAdd_time(newworkDateAll);
                    resultData.setAdd_time_name(add_time_name);
                } catch (Exception ignore) {
                    logger.error(ignore.toString());
                    continue;
                }
                int catIdByText = -1;
                try {
                    String catId = single[1];
                    catIdByText = getCatIdByText(catId);
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                logger.info("cityId: " + cityIdRelu);
                resultData.setCity_id(cityIdRelu);
                String title = single[1];
                resultData.setTitle(title);
                resultData.setDescription(title);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("获取json失败：" + e, e);
        }
        return allResults;
    }
}
