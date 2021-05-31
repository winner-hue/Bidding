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
import java.util.List;

public class CCGP_JiangXi extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_JiangXi.class);

    @Override
    protected void setValue() {
        titleRelu = "h1";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "p.ewb-location-content span";
        // 发布时间规则
        addTimeRelu = "span.ewb-list-date";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd";
        // 内容规则
        fullcontentRelu = "div.article-info";
        // 列表url节点规则
        nodeListRelu = "div#gengerlist li.ewb-list-node";
        // 城市代码
        cityIdRelu = 29;
        // 附件规则
        fjxxurlRelu = "div.con.attach a";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.jiangxi.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected String getAnnex(Document parse) {
        List<String> pdfList = new ArrayList<String>();
        try {
            Elements pdfs = parse.select(this.fjxxurlRelu);
            for (Element pdf : pdfs) {
                String href = pdf.attr("href");
                String pdfUrl = "http://www.ccgp-jiangxi.gov.cn" + href;
                pdfList.add(pdfUrl);
            }
            if (pdfList.size() > 0) {
                return pdfList.toString();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    @Override
    protected String getUrl(Element element) {
        Element a = element.select("a").get(0);
        String href = a.attr("href");
        String url = "http://www.ccgp-jiangxi.gov.cn" + href;
        return url;
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
                // 获取发布时间
                long addTime = getAddTime(element).getTime();
                logger.info("addTime: " + addTime);
                if (addTime - this.deadDate.getTime() < 0) {
                    logger.info("发布时间早于截止时间， 不添加该任务url");
                    continue;
                }
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                String add_time_name = format.format(addTime);
                resultData.setAdd_time(addTime);
                resultData.setAdd_time_name(add_time_name);
                resultData.setCity_id(this.cityIdRelu);
                allResults.add(resultData);
            } catch (Exception e) {
                logger.error("提取链接错误：" + e, e);
            }
        }
        return allResults;
    }


    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = null;
        int pageId = Integer.parseInt(Util.match("(\\d+).html", url)[1]);
        nextPageUrl = url.replaceAll("\\d+.html", (pageId + 1) + ".html");
        return nextPageUrl;
    }
}
