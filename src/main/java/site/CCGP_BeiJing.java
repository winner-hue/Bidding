package site;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import start.Bidding;
import util.Util;

public class CCGP_BeiJing extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_BeiJing.class);

    @Override
    protected void setValue() {
        titleRelu = "div.div_hui+div";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "div.div_hui";
        // 城市id规则
        cityIdRelu = 4;
        // 采购人规则
        authorRelu = "";
        // 价格规则
        priceRelu = "";
        // 发布时间规则
        addTimeRelu = "span.datetime";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd";
        // 内容规则
        fullcontentRelu = "table#queryTable|div[align='left']";
        // 附件规则
        fjxxurlRelu = "table#queryTable a";
        // 列表url节点规则
        nodeListRelu = "ul.xinxi_ul li";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.beijing.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
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
            url = this.baseUrl.replaceAll("index.*", "") + href;
        }
        return url;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            nextPageUrl = url.replaceAll("index.*", "index_" + (currentPage + 1) + ".html");
            logger.info("nextPageUrl: " + nextPageUrl);
        } catch (Exception ignore) {
        }
        return nextPageUrl;
    }

    @Override
    protected String getAuthor(Document parse) {
        try {
            return parse.select("td:matchesOwn(采购人)+td").get(0).text();
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select("td:matchesOwn(合同金额)+td").get(0).text();
        } catch (Exception e) {
            try {
                String html = parse.html();
                if (html.contains("预算金额")) {
                    return Jsoup.parse(Util.match("预算金额：(.*)", parse.html())[1]).text();
                }
                if (html.contains("总中标成交金额")) {
                    return Jsoup.parse(Util.match("总中标成交金额：(.*)", parse.html())[1]).text();
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    @Override
    protected int getCatId(Document parse) {
        try {
            String text = parse.select(this.catIdRelu).get(0).text();
            String[] split = text.split("->");

            return getCatIdByText(split[split.length - 1]);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    protected String getTitle(Document parse) {
        try {
            String text = "";
            if (parse.select(this.titleRelu).size() > 0) {
                text = parse.select(this.titleRelu).get(0).text();
            } else  {
                text = parse.select("span[style='font-size: 20px;font-weight: bold']").get(0).text();
            }
            return text;
        } catch (Exception e) {
            return "";
        }
    }
}
