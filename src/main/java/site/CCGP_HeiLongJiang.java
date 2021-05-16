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

public class CCGP_HeiLongJiang extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_HeiLongJiang.class);
    private static HashMap<String, String> heads = new HashMap<String, String>();
    private static String relu = "{'2cd7f79ead224e40b33557350b1404d2': {\n" +
            "    'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额) + td', 'price_unit': '万元'},\n" +
            "    'ac97d61fa3734f16a266822e697c8763': {\n" +
            "        'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额) + td', 'price_unit': '万元'},\n" +
            "    '463fa57862ea4cc79232158f5ed02d03': {\n" +
            "        'priceRelu': 'span:containsOwn(合同包预算金额：)|div.notice-con ~ table tbody tr:eq(0) td:eq(2)', 'price_unit': '元'},\n" +
            "    '7dc00df822464bedbf9e59d02702b714': {\n" +
            "        'priceRelu': ''},\n" +
            "    'b716da75fe8d4e4387f5a8c72ac2a937': {\n" +
            "        'priceRelu': 'div.customize_purchaseResult table tbody tr:eq(1) td:eq(2)|div.notice-con table tr:eq(12) td:eq(1) p span',\n" +
            "        'price_unit': '万元'},\n" +
            "    'd812e46569204c7fbd24cbe9866d0651': {\n" +
            "        'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额) + td|label:containsOwn(合同金额：) + div span',\n" +
            "        'price_unit': '万元'},\n" +
            "    '1d5eac5cd0b14515aacaf2e9aee5f928': {\n" +
            "        'priceRelu': 'span:containsOwn(合同总金额：￥) span', 'price_unit': '元'},\n" +
            "    'ce932df3036340559c19acc4935c04b9': {\n" +
            "        'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额)'},\n" +
            "    '255e087cf55a42139a1f1b176b244ebb': {\n" +
            "        'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额) + td'},\n" +
            "    '30f19b25203f11e8b43a060400ef5315': {\n" +
            "        'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额) + td'},\n" +
            "    '30f19b24203f11e8b43a060400ef5315': {\n" +
            "        'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额) + td'},\n" +
            "    '8fd455f244cc11eb88b50cda411d946b': {\n" +
            "        'priceRelu': 'div.table table tbody tr td:containsOwn(预算金额) + td'}}";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        String url = "http://hljcg.hlj.gov.cn/freecms/rest/v1/notice/selectInfoMoreChannel.do?&siteId=94c965cc-c55d-4f92-8469-d5875c68bd04&channel=c5bff13f-21ca-4dac-b158-cb40accd3035&currPage=1&pageSize=8&noticeType=00101&regionCode=&title=&openTenderCode=&purchaser=&agency=&purchaseNature=&operationStartTime=&operationEndTime=&selectTimeName=noticeTime";
        final String[] purchaseManners = {"1", "2", "3", "4", "5", "6", "9"}, urls = new String[purchaseManners.length];
        for (int i = 0; i < purchaseManners.length; i++) {
            urls[i] = url.concat("&purchaseManner=").concat(purchaseManners[i]);
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 10;
        fullcontentRelu = "div#print-content";
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
            this.baseUrl = url.split("/freecms")[0];
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return url.replaceAll("currPage=(\\d+)", "currPage=" + (currentPage + 1));
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url, heads);
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
                    currentPage = Integer.parseInt(Util.match("currPage=(\\d+)", url)[1]);
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
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        JSONArray rows = JSONObject.parseObject(httpBody).getJSONArray("data");
        for (int i = 0; i < rows.size(); i++) {
            logger.info("===========================================");
            StructData resultData = new StructData();
            JSONObject jo = rows.getJSONObject(i);
            try {
                String hits = jo.getString("addtimeStr");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = format.parse(hits);
                long addTime = date.getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    return allResults;
                }
                // 获取链接
                String url = this.baseUrl + jo.getString("pageurl") + "?noticeType=00101";
                logger.info("url: " + url);
                resultData.setArticleurl(url);
                String title = jo.getString("title");
                resultData.setTitle(title);
                resultData.setDescription(title);
                format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                resultData.setCity_id(this.cityIdRelu);
                String author = jo.getJSONObject("fieldValues").getString("f_purchaser");
                resultData.setAuthor(author);
                String budget = jo.getJSONObject("fieldValues").getString("f_budget");
                resultData.setPrice(budget);
                int catIdByText = -1;
                try {
                    String cst = jo.getJSONObject("fieldValues").getString("f_noticeTypeName");
                    catIdByText = getCatIdByText(cst);
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
