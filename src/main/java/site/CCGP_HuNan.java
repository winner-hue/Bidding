package site;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CCGP_HuNan extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_HeNan.class);

    @Override
    protected void setValue() {
        titleRelu = "p.danyi_title";
        priceRelu = "td:matchesOwn(预算金额：)";
        fullcontentRelu = "body";
        cityIdRelu = 19;
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String base_url = "http://www.ccgp-hunan.gov.cn/mvc/getNoticeList4Web.do";
        String[] urlsTemp = Bidding.properties.getProperty("ccgp.hunan.url").split(",");
        String[] urls = new String[urlsTemp.length];
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String endDate = format.format(new Date(System.currentTimeMillis()));
        String startDate = format.format(new Date(System.currentTimeMillis() - 3 * 30 * 24 * 60 * 60 * 1000L));
        for (int i = 0; i < urlsTemp.length; i++) {
            if (urlsTemp[i].contains("moreCityCounty")) {
                String noticeTypeID = Util.match("noticeTypeID=(.*)", urlsTemp[i])[1];
                urls[i] = "http://www.ccgp-hunan.gov.cn/mvc/getNoticeListOfCityCounty.do" + "&#44nType=" + noticeTypeID + "pType=&prcmPrjName=&prcmItemCode=&prcmOrgName=&startDate=" + startDate + "&endDate=" + endDate + "&prcmPlanNo=&page=1&pageSize=18";
            } else if (urlsTemp[i].contains("noticeTypeID")) {
                String noticeTypeID = Util.match("noticeTypeID=(.*)", urlsTemp[i])[1];
                urls[i] = base_url + "&#44nType=" + noticeTypeID + "pType=&prcmPrjName=&prcmItemCode=&prcmOrgName=&startDate=" + startDate + "&endDate=" + endDate + "&prcmPlanNo=&page=1&pageSize=18";
            }
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        try {
            JSONArray rows = null;
            try {
                rows = JSONObject.parseObject(httpBody).getJSONArray("rows");
            } catch (Exception e) {
                rows = JSONArray.parseArray(httpBody);
            }
            for (int i = 0; i < rows.size(); i++) {
                logger.info("===========================================");
                JSONObject jo = rows.getJSONObject(i);
                StructData resultData = new StructData();
                try {
                    String noticeId = jo.getString("NOTICE_ID");
                    int AREA_ID = Integer.parseInt(jo.getString("AREA_ID"));
                    String url;
                    if (AREA_ID < 0) {
                        url = "http://www.ccgp-hunan.gov.cn/mvc/viewNoticeContent.do?noticeId=" + noticeId;
                    } else {
                        url = "http://www.ccgp-hunan.gov.cn/mvc/viewNoticeContent.do?noticeId=" + noticeId + "&area_id=" + AREA_ID;
                    }
                    String title = jo.getString("NOTICE_TITLE");
                    logger.info("url: " + url);
                    resultData.setArticleurl(url);
                    resultData.setTitle(title);
                    String md5 = Util.stringToMD5(url);
                    logger.info("md5: " + md5);
//                    //resultData.setMd5(md5);
                } catch (Exception e) {
                    continue;
                }
                try {
                    long newworkDateAll = jo.getJSONObject("NEWWORK_DATE_ALL").getLong("time");
                    logger.info("addTime: " + newworkDateAll);
                    if (newworkDateAll - this.deadDate.getTime() < 0) {
                        logger.info("发布时间早于截止时间， 不添加该任务url");
                        continue;
                    }
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String add_time_name = format.format(newworkDateAll);
                    resultData.setAdd_time(newworkDateAll);
                    resultData.setAdd_time_name(add_time_name);
                } catch (Exception ignore) {
                }

                int catIdByText = -1;
                try {
                    String catId = jo.getString("NOTICE_NAME");
                    catIdByText = getCatIdByText(catId);
                    logger.info("catId: " + catIdByText);
                } catch (Exception ignore) {
                }
                resultData.setCat_id(catIdByText);
                logger.info("cityId: " + this.cityIdRelu);
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            }
        } catch (Exception e) {
            logger.error("获取json失败：" + e, e);
        }
        return allResults;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("page=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("page=\\d+", "page=" + (Integer.parseInt(id) + 1));
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select(this.priceRelu).get(0).text().replaceAll("预算金额：", "");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getDetail(Document parse) {
        try {
            String content = "";
            try {
                Elements elements = parse.select(fullcontentRelu);
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < elements.size(); i++) {
                    builder.append(elements.get(i).html());
                }
                content = builder.toString();
            } catch (Exception ignore) {
            }
            return content;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");
//        String title = getTitle(parse);
//        logger.info("title: " + title);
//        data.setTitle(title);
        String description = getDescription(parse);
        logger.info("description: " + description);
        data.setDescription(description);
        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);
        String author = getAuthor(parse);
        logger.info("author: " + author);
        data.setAuthor(author);
        String price = getPrice(parse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
        String add_time_name = data.getAdd_time_name();
        data.setAdd_time_name(add_time_name);
    }

    @Override
    protected String getTitle(Document parse) {
        String title;
        try {
            title = parse.select(this.titleRelu).get(0).text();
        } catch (Exception e) {
            try {
                title = parse.select("p[align='center']").get(0).text();
            } catch (Exception ignore) {
                title = "";
            }
        }
        return title;
    }
}
