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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_LiaoNing extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_LiaoNing.class);
    private static JSONObject cats;

    @Override
    protected void setValue() {
        priceRelu = "span:has(font:matchesOwn(预算金额：)) + b span font,font:matchesOwn(预算金额：) + font,span:matchesOwn(预算金额（元）：),span:matchesOwn(预算金额：) u";
        fullcontentRelu = "input#retval,div.control-label-text";
        authorRelu = "font:matchesOwn(采购人：),p:has(span:has(font:matchesOwn(.采购人信息))) + p u span font,div:has(span:matchesOwn(.采购人信息)) + div span:eq(1),span:matchesOwn(采购人：)";
        cityIdRelu = 17;
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.liaoning.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        if (httpBody.contains("502 Bad Gateway")) {
            startRun(retryTime, url, currentPage);
        }
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = null;
            int nums = 5;
            while (nums > 0) {
                try {
                    pageSource = getHttpBody(retryTime, tempUrl);
                    if (pageSource.contains("502 Bad Gateway")) {
                        nums = nums - 1;
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    nums = nums - 1;
                }
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
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONArray rows = JSONObject.parseObject(httpBody).getJSONArray("rows");
            for (int i = 0; i < rows.size(); i++) {
                logger.info("===========================================");
                JSONObject jo = rows.getJSONObject(i);
                StructData resultData = new StructData();
                String id = jo.getString("id");
                String url = "http://www.ccgp-liaoning.gov.cn/portalindex.do?method=getPubInfoViewOpenNew&infoId=" + id;
                resultData.setArticleurl(url);

                try {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    String releaseDate = jo.getString("releaseDate");
                    long addTime = format.parse(releaseDate).getTime();
                    logger.info("addTime: " + addTime);
                    if (addTime - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        return allResults;
                    }
                    String add_time_name = format.format(addTime);
                    resultData.setAdd_time(addTime);
                    resultData.setAdd_time_name(add_time_name);
                    resultData.setCity_id(this.cityIdRelu);
                } catch (Exception ignore) {
                }
                int catId = -1;
                try {
                    String catIdByText = jo.getString("infoTypeName");
                    catId = getCatIdByText(catIdByText);
                    logger.info("catId: " + catId);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catId);
                logger.info("cityId: " + this.cityIdRelu);
                resultData.setCity_id(this.cityIdRelu);
                String title = jo.getString("title");
                logger.info("title: " + title);
                resultData.setTitle(title);
                resultData.setDescription(title);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("获取json失败：" + e, e);
        }
        return allResults;
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");
        Element template = null;
        template = parse.getElementById("template");
        Document newHtmlParse = null;
        try {
            newHtmlParse = Jsoup.parse(template.text());
        } catch (Exception e) {
            newHtmlParse = parse;
        }
        if (data.getPrice() == null) {
            String price = null;
            try {
                price = getPrice(newHtmlParse);
                logger.info("price: " + price);
            } catch (Exception e) {
                e.printStackTrace();
            }
            data.setPrice(price);
        }
        if (data.getAuthor() == null) {
            String purchaser = null;
            try {
                purchaser = getAuthor(newHtmlParse);
                purchaser = purchaser.contains("：") ? purchaser.split("：")[1] : purchaser;
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info("purchaser: " + purchaser);
            data.setAuthor(purchaser);
        }
        String detail = newHtmlParse.outerHtml().replaceAll("'", "\"");
        logger.info("detail length: " + detail.length());
        data.setFullcontent(detail);
        String annex = getAnnex(newHtmlParse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    @Override
    protected String getDetail(Document parse) {
        String content;
        try {
            content = parse.select(fullcontentRelu).get(0).outerHtml();
        } catch (Exception e) {
            return "";
        }
        return content;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("current=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("current=\\d+", "current=" + (Integer.parseInt(id) + 1));
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }

    @Override
    protected String getPrice(Document parse) {
        String price = null;
        try {
            if (parse.select("td:containsOwn(预算金额（万元）)").size() > 0) {
                double pe = 0;
                Elements els = parse.select("tbody tr td:eq(3)");
                for (Element el : els) {
                    try {
                        pe = pe + Double.parseDouble(el.text().replaceAll(",", ""));
                    } catch (NumberFormatException e) {
                    }
                }
                price = pe + "万元";
            } else {
                price = parse.select(this.priceRelu).get(0).text().replaceAll(".*项目预算金额：", "");
            }
        } catch (Exception e) {
            return "";
        }
        price = Util.HasDigit(price) ? price.contains("：") ? price.split("：")[1] : price : price;
        return price;
    }
}
