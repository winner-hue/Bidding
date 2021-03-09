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
import java.util.*;

import static util.Download.getHttpBody;

public class CCGP_YunNan extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_YunNan.class);
    private static HashMap<String, String> heads = new HashMap<String, String>();
    private static String relu = "{'1': {'authorRelu': 'div.table table tbody tr td:containsOwn(采购单位) + td', 'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额) + td',\n" +
            "            'fullcontentRelu': 'div.vF_detail_content'},\n" +
            "      '4': {'authorRelu': 'div.table table tbody tr td:containsOwn(采购单位) + td', 'priceRelu': 'div.table table tbody tr td:containsOwn(总中标金额) + td',\n" +
            "            'fullcontentRelu': 'div.vF_detail_content'},\n" +
            "      '3': {'authorRelu': 'label:containsOwn(预算单位：) + div', 'priceRelu': 'label:containsOwn(预算金额：) + div',\n" +
            "            'fullcontentRelu': 'div.panel-body'},\n" +
            "      '5': {'authorRelu': 'label:containsOwn(预算单位：) + div', 'priceRelu': 'label:containsOwn(预算金额：) + div',\n" +
            "            'fullcontentRelu': 'div.panel-body form-horizontal'},\n" +
            "      '2': {'authorRelu': 'div.table table tbody tr td:containsOwn(采购单位) + td', 'priceRelu': 'div.table table tbody tr td:containsOwn(总成交金额) + td',\n" +
            "            'fullcontentRelu': 'div.vF_detail_content'},\n" +
            "      '7': {'authorRelu': 'div.table table tbody tr td:containsOwn(采购单位) + td', 'priceRelu': '',\n" +
            "            'fullcontentRelu': 'div.vF_detail_content'},\n" +
            "      '6': {'authorRelu': 'span:containsOwn(采购人（甲方）)', 'priceRelu': 'span:containsOwn(合同金额)'}\n" +
            "      }";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        heads.put("Referer", "http://www.ccgp-yunnan.gov.cn/bulletin.do?method=moreList");
        String url = "http://www.ccgp-yunnan.gov.cn/bulletin.do?method=moreListQuery";
//        final String[] query_signs = {"1", "4", "3", "5", "2", "7", "6"}, urls = new String[query_signs.length];
        final String[] query_signs = {"6"}, urls = new String[query_signs.length];
        for (int i = 0; i < query_signs.length; i++) {
            urls[i] = url.concat("&#44current=1&rowCount=20&query_sign=".concat(query_signs[i]));
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 2;
        titleRelu = "h1";
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
            this.baseUrl = url;
            startRun(retryTime, url, 0);
        }
    }

    protected String getNextPageUrl(int currentPage, String url) {
        return url.replaceAll("current=(\\d+)", "current=" + (currentPage + 1));
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        String query_sign = Util.match("query_sign=(\\d+)", url)[1];
        List<StructData> allResult = getAllResult(httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = getHttpBody(retryTime, tempUrl, heads);
            Document parse = Jsoup.parse(pageSource);
            extract(parse, data, query_sign);
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
                    currentPage = Integer.parseInt(Util.match("current=(\\d+)&", url)[1]);
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
    protected void extract(Document parse, StructData data, String query_sign) {
        logger.info("==================================");
        String description = data.getTitle();
        logger.info("description: " + description);
        data.setDescription(description);
        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);
        String author = getAuthor(parse, query_sign);
        logger.info("author: " + author);
        data.setAuthor(author);
        String price = getPrice(parse, query_sign);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse, query_sign);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse, query_sign);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    protected String getAnnex(Document parse, String query_sign) {
        return super.getAnnex(parse);
    }

    protected String getDetail(Document parse, String query_sign) {
        String detail_str = null;
        try {
            detail_str = parse.select(relus.getJSONObject(query_sign).get("fullcontentRelu").toString()).html();
        } catch (Exception e) {
            return "";
        }
        return detail_str;
    }

    protected String getPrice(Document parse, String query_sign) {
        String price_str = null;
        try {
            price_str = parse.select(relus.getJSONObject(query_sign).get("priceRelu").toString()).get(0).text();
        } catch (Exception e) {
            return "";
        }
        return price_str;
    }

    protected String getAuthor(Document parse, String query_sign) {
        String author = null;
        try {
            author = parse.select(relus.getJSONObject(query_sign).get("authorRelu").toString()).get(0).text();
        } catch (Exception e) {
            return "";
        }
        return author;
    }

    @Override
    protected int getCatId(Document parse) {
        return super.getCatId(parse);
    }

    @Override
    protected String getDescription(Document parse) {
        return super.getDescription(parse);
    }

    @Override
    protected String getTitle(Document parse) {
        return super.getTitle(parse);
    }

    @Override
    protected String getUrl(Element element) {
        return super.getUrl(element);
    }

    @Override
    protected Date getAddTime(Element element) {
        return super.getAddTime(element);
    }

    protected List<StructData> getAllResult(String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONArray rows = JSONObject.parseObject(httpBody).getJSONArray("rows");
            for (int i = 0; i < rows.size(); i++) {
                logger.info("===========================================");
                JSONObject jo = rows.getJSONObject(i);
                StructData resultData = new StructData();
                if (jo.get("finishday").toString().length() < 1) {
                    continue;
                }
                try {
                    String url = showUrl(jo.getString("bulletin_id"), jo.getString("bulletinclass"), jo.getString("tabletype"));
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                } catch (Exception e) {
                    continue;
                }
                try {
                    String hits = jo.getString("finishday");
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    Date date = format.parse(hits);
                    long addTime = date.getTime();
                    logger.info("addTime: " + addTime);
                    if (addTime - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        break;
                    }
                    format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String add_time_name = format.format(addTime);
                    resultData.setAdd_time(addTime);
                    resultData.setAdd_time_name(add_time_name);
                } catch (Exception ignore) {
                }
                int catIdByText = -1;
                try {
                    String catId = jo.getString("bulletinclasschina");
                    catIdByText = getCatIdByText(catId);
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                logger.info("cityId: " + cityIdRelu);
                resultData.setCity_id(cityIdRelu);
                String title = jo.getString("bulletintitle");
                resultData.setTitle(title);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("获取json失败：" + e, e);
        }
        return allResults;
    }

    protected String showUrl(String bulletin_id, String bulletinclass, String tabletype) {
        String host_url = "http://www.ccgp-yunnan.gov.cn", url = "";
        if (bulletin_id.length() > 0 && "sddfucggg".equals(bulletin_id)) {
            url = host_url + "/governmentpolicy.do?method=designatedServiceMain";
        } else {
            if (tabletype.equals("3") || tabletype.equals("4")) {
                if (bulletinclass.length() > 0 && bulletinclass.equals("bxlx014")) {
                    url = host_url + "/contract.do?method=showContractDetail&bulletinclass=bxlx014&bulletin_id=" + bulletin_id;
                } else {
                    url = host_url + "/bulletin_zz.do?method=shownotice&bulletin_id=" + bulletin_id;
                }
            } else if (tabletype.equals("5")) {
                url = host_url + "/bulletin_zz.do?method=showBulletin&bulletin_id=" + bulletin_id + "&sign=5";
            } else {
                url = host_url + "/bulletin_zz.do?method=showBulletin&bulletin_id=" + bulletin_id + "&bulletinclass=" + bulletinclass;
            }
        }
        return url;
    }
}
