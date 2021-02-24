package site;

import org.jsoup.Jsoup;
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

public class CCGP_HaiNan extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_HaiNan.class);

    @Override
    protected void setValue() {
        titleRelu = "div.zx-xxxqy h2";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "div.zx-xxxqy h2";
        // 价格规则
        priceRelu = "span i";
        // 内容规则
        fullcontentRelu = "div.content01";
        // 列表url节点规则
        nodeListRelu = "div.index07_07_02 ul li";
        // 城市代码
        cityIdRelu = 26;

        addTimeRelu = "span em";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urlsTemp = Bidding.properties.getProperty("ccgp.hainan.url").split(",");
        String[] urls = new String[urlsTemp.length];
        for (int i = 0; i < urlsTemp.length; i++) {
            urls[i] = urlsTemp[i] + "&#44title=&bid_type=&proj_number=&begindate=&enddate=&zone=&currentPage=1";
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
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
                // 获取链接md5值， 用于排重
                String md5 = Util.stringToMD5(url);
                logger.info("md5: " + md5);
                //resultData.setMd5(md5);
                // 获取发布时间
                long addTime = getAddTime(element).getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    continue;
                }
                resultData.setAdd_time(addTime);
                resultData.setCity_id(this.cityIdRelu);

                String price = getPrice(Jsoup.parse(element.html()));
                logger.info("price: " + price);
                resultData.setPrice(price);
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
        String title = getTitle(parse);
        logger.info("title: " + title);
        data.setTitle(title);
        String description = getDescription(parse);
        logger.info("description: " + description);
        data.setDescription(description);
        int catId = getCatId(parse);
        logger.info("catId: " + catId);
        data.setCat_id(catId);
        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);
        String purchaser = getAuthor(parse);
        logger.info("purchaser: " + purchaser);
        data.setAuthor(purchaser);

        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            String content = parse.select(this.priceRelu).text();
            return Util.match("金额：(.*万元)", content)[1];
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("currentPage=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("currentPage=\\d+", "currentPage=" + (Integer.parseInt(id) + 1));
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
            url = "https://www.ccgp-hainan.gov.cn" + href;
        } else {
            url = href;
        }
        return url;
    }

    @Override
    protected String getAnnex(Document parse) {
        List<String> pdfList = new ArrayList<String>();
        try {
            Elements pdfs = parse.select("a");
            for (Element pdf : pdfs) {
                String href = pdf.attr("href");
                if (href.contains(".pdf") || href.contains(".doc") || href.contains(".xlsx") || href.contains(".xls")) {
                    if (href.startsWith("http")) {
                        pdfList.add(href);
                    } else {
                        href = "https://www.ccgp-hainan.gov.cn" + href;
                        pdfList.add(href);
                    }

                }
            }
            if (pdfList.size() > 0) {
                return pdfList.toString();
            }
        } catch (Exception ignore) {
        }
        return null;
    }
}
