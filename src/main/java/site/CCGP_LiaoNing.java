package site;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CCGP_LiaoNing extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_LiaoNing.class);

    @Override
    protected void setValue() {
        priceRelu = "span:matchesOwn(预算金额)";
        fullcontentRelu = "div[style='font-size: 12pt;']";
        cityIdRelu = 17;
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] tempUrls = Bidding.properties.getProperty("ccgp.liaoning.url").split(",");
        String[] urls = new String[tempUrls.length];
        for (int i = 0; i < tempUrls.length; i++) {
            urls[i] = tempUrls[i] + "&#44current=1&rowCount=10&searchPhrase=&infoTypeCode=1001&privateOrCity=1";
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
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
                try {
                    String id = jo.getString("id");
                    String url = "http://www.ccgp-liaoning.gov.cn/portalindex.do?method=getPubInfoViewOpenNew&infoId=" + id;
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                    String md5 = Util.stringToMD5(url);
                    logger.info("md5: " + md5);
                    //resultData.setMd5(md5);
                } catch (Exception e) {
                    continue;
                }
                try {
                    String releaseDate = jo.getString("releaseDate");
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    long addTime = format.parse(releaseDate).getTime();
                    logger.info("addTime: " + addTime);
                    if (addTime - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        continue;
                    }
                    resultData.setAdd_time(addTime);
                } catch (Exception ignore) {
                }

                int catIdByText = -1;
                try {
                    String catId = jo.getString("infoTypeName");
                    catIdByText = getCatIdByText(catId);
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                logger.info("cityId: " + this.cityIdRelu);
                resultData.setCity_id(this.cityIdRelu);

                try {
                    String title = jo.getString("title");
                    logger.info("title: " + title);
                    resultData.setTitle(title);
                } catch (Exception ignore) {
                }
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
        Element template = parse.getElementById("template");
        Document newHtmlParse = Jsoup.parse(template.text());

        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);

        String price = getPrice(newHtmlParse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(newHtmlParse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(newHtmlParse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
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
        try {
            return parse.select(this.priceRelu).get(0).text().replaceAll("预算金额（元）：", "").replaceAll("预算金额：", "").replaceAll("。", "").trim();
        } catch (Exception e) {
            return "";
        }
    }
}
