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

public class CCGP_HeBei extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_HeBei.class);

    @Override
    protected void setValue() {
        titleRelu = "span.txt2";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "td#navi";
        // 采购人规则
        authorRelu = "span.txt:nth-child(3)";
        // 价格规则
        priceRelu = "span#amt";
        // 发布时间规则
        addTimeRelu = "span.txt:nth-child(1)";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd";
        // 内容规则
        fullcontentRelu = "span.txt7";
        // 附件规则
        fjxxurlRelu = "span[id^=fujian_]";
        // 列表url节点规则
        nodeListRelu = "table#moredingannctable td.txt1";
        // 城市代码
        cityIdRelu = 5;
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.hebei.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        List<StructData> allResults = new ArrayList<StructData>();
        Elements cListBid = parse.select(this.nodeListRelu);
        Elements urlsList = parse.select("table#moredingannctable a");
        for (int i = 0; i < cListBid.size(); i++) {
            Element element = cListBid.get(i);
            logger.info("===========================================");
            StructData resultData = new StructData();
            try {
                // 获取链接
                String url = getUrl(urlsList.get(i));
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

                String purchaser = getAuthor(parse);
                resultData.setAuthor(purchaser);
                logger.info("purchaser: " + purchaser);
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
        String price = getPrice(parse);
        logger.info("price: " + price);
        data.setPrice(price);
        String detail = getDetail(parse);
        logger.info("detail: " + detail);
        data.setFullcontent(detail);
        String annex = getAnnex(parse);
        logger.info("annex: " + annex);
        data.setFjxxurl(annex);
    }

    @Override
    protected int getCatId(Document parse) {
        try {
            String text = parse.select(this.catIdRelu).get(0).ownText();
            return getCatIdByText(text);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    protected String getAnnex(Document parse) {
        List<String> annex = new ArrayList<String>();
        try {
            String[] cons = parse.getElementById("con").text().split("#detail#");
            String[] detail = cons[1].split("@_@");
            for (int i = 0; i < detail.length; i++) {
                try {
                    String[] file = detail[i].split("#_#");
                    String url = "http://www.ccgp-hebei.gov.cn/BidDingAnncFiles/" + file[2] + "." + file[1];
                    annex.add(url);
                } catch (Exception ignore) {
                }
            }
        } catch (Exception ignore) {
        }
        return annex.toString();
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            String id = Util.match("page=(\\d+)", url)[1];
            nextPageUrl = url.replaceAll("page=\\d+&ch", "page=" + (Integer.parseInt(id) + 1) + "&ch");
        } catch (Exception ignore) {
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }
}
