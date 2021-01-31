package site;

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

public class CCGP_ShanXi extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_ShanXi.class);

    @Override
    protected void setValue() {
        titleRelu = "h1";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "td.zt1 a";
        // 价格规则
        priceRelu = "p:matchesOwn(预算金额)";
        // 内容规则
        detailRelu = "div.inner-Box";
        // 列表url节点规则
        nodeListRelu = "tbody tr";
        // 城市代码
        cityIdRelu = 16;
        addTimeRelu = "td";
    }
    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.shanxi.url").split(",");
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
                resultData.setUrl(url);
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

                if (baseUrl.contains("noticetype=3")) {
                    logger.info("catId: " + 1);
                    resultData.setCat_id(1);
                }else if  (baseUrl.contains("noticetype=5")) {
                    logger.info("catId: " + 7);
                    resultData.setCat_id(7);
                }else if  (baseUrl.contains("noticetype=4")) {
                    logger.info("catId: " + 8);
                    resultData.setCat_id(8);
                }else if  (baseUrl.contains("noticetype=6")) {
                    logger.info("catId: " + 12);
                    resultData.setCat_id(12);
                }else if  (baseUrl.contains("noticetype=99")) {
                    logger.info("catId: " + 9);
                    resultData.setCat_id(9);
                }else if  (baseUrl.contains("noticetype=1")) {
                    logger.info("catId: " + 13);
                    resultData.setCat_id(13);
                }else if  (baseUrl.contains("noticetype=301")) {
                    logger.info("catId: " + 13);
                    resultData.setCat_id(13);
                } else {
                    logger.info("catId: " + (-1));
                    resultData.setCat_id(-1);
                }

                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("page.pageNum=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("page.pageNum=\\d+", "page.pageNum=" + (Integer.parseInt(id) + 1));
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        logger.info("==================================");
        String title = getTitle(parse);
        logger.info("title: " + title);
        data.setTitle(title);
        int cityId = cityIdRelu;
        logger.info("cityId: " + cityId);
        data.setCity_id(cityId);
        String price = getPrice(parse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setDetail(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setAnnex(annex);
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select(this.priceRelu).get(0).text().replaceAll(".*预算金额：", "");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected Date getAddTime(Element element) {
        Date parse = null;
        try {
            String addTime = element.select(this.addTimeRelu).last().text();
            SimpleDateFormat format = new SimpleDateFormat(this.addTimeParse);
            parse = format.parse(addTime);
        } catch (Exception e) {
            logger.error("获取日期错误：" + e, e);
        }
        return parse;
    }
}
