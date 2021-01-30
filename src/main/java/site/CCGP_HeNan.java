package site;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.Download;
import util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CCGP_HeNan extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_HeNan.class);
    private String fujian = "";


    @Override
    protected void setValue() {
        titleRelu = "a";
        descriptionRelu = "";
        priceRelu = "td:matchesOwn(项目预算金额：)";
        addTimeRelu = "span.Gray.Right";
        addTimeParse = "yyyy-MM-dd HH:mm";
        detailRelu = "table.Content";
        annexRelu = "div.List1.Top5 a";
        nodeListRelu = "div.List2 li";
        cityIdRelu = 6;
        catIdRelu = "a";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.henan.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select(this.priceRelu).get(0).text().replaceAll(".*项目预算金额：", "");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("pageNo=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("pageNo=\\d+", "pageNo=" + (Integer.parseInt(id) + 1));
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }

    @Override
    protected String getUrl(Element element) {
        Element a = element.select("a").get(0);
        String href = a.attr("href");

        if (href == null || "".equals(href)) {
            return null;
        }
        if (href.startsWith("./")) {
            href = href.substring(2);
        }
        String url = null;
        if (!href.startsWith("http")) {
            url = "http://www.ccgp-henan.gov.cn/" + href;
        } else {
            url = href;
        }
        try {
            String httpBody = Download.getHttpBody(3, url);
            fujian = getAnnex(Jsoup.parse(httpBody));
            String urlMatched = Util.match("get\\(\"(.*)\",", httpBody)[1];
            if (!urlMatched.startsWith("/")) {
                url = "http://www.ccgp-henan.gov.cn/" + urlMatched;
            } else {
                url = "http://www.ccgp-henan.gov.cn" + urlMatched;
            }
        } catch (Exception ignore) {
        }
        return url;
    }

    @Override
    protected String getAnnex(Document parse) {
        List<String> pdfList = new ArrayList<String>();
        try {
            Elements elements = parse.select(annexRelu);
            for (int i = 0; i < elements.size(); i++) {
                String href = elements.get(i).attr("href");
                if (!href.startsWith("http")) {
                    href = "http://www.ccgp-henan.gov.cn/" + href;
                }
                pdfList.add(href);
            }
            return pdfList.toString();
        } catch (Exception ignore) {
        }
        return null;
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
                resultData.setUrl(url);

                logger.info("annex: " + fujian);
                resultData.setAnnex(fujian);

                String title = getTitle(Jsoup.parse(element.html()));
                logger.info("title: " + title);
                resultData.setTitle(title);

                int catid = getCatId(Jsoup.parse(element.html()));
                logger.info("catId: " + catid);
                resultData.setCat_id(catid);

                // 获取链接md5值， 用于排重
                String md5 = Util.stringToMD5(url);
                logger.info("md5: " + md5);
                resultData.setMd5(md5);
                // 获取发布时间
                Date addTime = getAddTime(element);
                logger.info("addTime: " + addTime);
                if (addTime.getTime() - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    continue;
                }
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                resultData.setAdd_time(format.format(addTime));
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");

        String description = getDescription(parse);
        logger.info("description: " + description);
        data.setDescription(description);

        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);
        String purchaser = getPurchaser(parse);
        logger.info("purchaser: " + purchaser);
        data.setPurchaser(purchaser);
        String price = getPrice(parse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setDetail(detail);

    }
}
