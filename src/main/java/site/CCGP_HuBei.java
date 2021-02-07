package site;

import com.alibaba.druid.util.Base64;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import start.Bidding;
import sun.misc.BASE64Encoder;
import util.Util;

import java.util.ArrayList;
import java.util.List;

public class CCGP_HuBei extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_HuBei.class);

    @Override
    protected void setValue() {
        titleRelu = "h2";
        descriptionRelu = "div.art_con > p";
        priceRelu = "p:matchesOwn(预算金额：) span";
        addTimeRelu = "span";
        addTimeParse = "yyyy-MM-dd";
        fullcontentRelu = "div[style='margin: 0 22px;']";
        fjxxurlRelu = "ul.list-unstyled.details-ul a";
        nodeListRelu = "ul.news-list-content.list-unstyled li";
        cityIdRelu = 7;
        catIdRelu = "li.active";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.hubei.url").split(",");
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
            url = "http://www.ccgp-hubei.gov.cn/" + href;
        } else {
            url = href;
        }
        return url;
    }

    @Override
    protected String getDescription(Document parse) {
        try {
            return parse.select(this.descriptionRelu).text();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select(this.priceRelu).text();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {

        String nextPageUrl = null;
        try {
            nextPageUrl = url.substring(0, url.indexOf("index_") == -1 ? url.length() : url.indexOf("index_")) + "index_" + (currentPage + 1) + ".html";
        } catch (Exception e) {
            logger.error("获取下一页错误：" + e, e);
        }
        logger.info("nextPageUrl: " + nextPageUrl);
        return nextPageUrl;
    }

    @Override
    protected String getAnnex(Document parse) {

        List<String> pdfList = new ArrayList<String>();
        try {
            Elements pdfs = parse.select(fjxxurlRelu);
            for (Element pdf : pdfs) {
                try {
                    String href = pdf.attr("href");
                    String id = Util.match("64\\('(\\w+)'\\)", href)[1];
                    BASE64Encoder encoder = new BASE64Encoder();
                    String url = "http://www.ccgp-hubei.gov.cn:8090/gpmispub/download?id=" + encoder.encode(id.getBytes());
                    pdfList.add(url);
                } catch (Exception ignore) {
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
