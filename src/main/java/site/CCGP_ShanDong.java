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

public class CCGP_ShanDong extends WebGeneral {
    private Logger logger = LoggerFactory.getLogger(CCGP_ShanDong.class);

    @Override
    protected void setValue() {
        titleRelu = "h1.title";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "div.location";
        // 发布时间规则
        addTimeRelu = "span.hits";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd";
        // 内容规则
        fullcontentRelu = "div#textarea";
        // 列表url节点规则
        nodeListRelu = "ul.news_list2 li";
        // 城市代码
        cityIdRelu = 9;
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.shandong.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = null;
        int pageId = Integer.parseInt(Util.match("curpage=(\\d+)", url)[1]);
        nextPageUrl = url.replaceAll("curpage=\\d+", "curpage=" + (pageId + 1));
        return nextPageUrl;
    }

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
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
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
    protected String getUrl(Element element) {
        Element a = element.select("a").get(0);
        String href = a.attr("href");
        String url = "http://www.ccgp-shandong.gov.cn" + href;
        return url;
    }

}
