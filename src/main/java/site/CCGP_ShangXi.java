package site;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import start.Bidding;
import util.Util;

import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * 该站点有反扒措施，访问快会线程卡死, 山西
 */
public class CCGP_ShangXi extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_ShangXi.class);

    @Override
    protected void setValue() {
        titleRelu = "div h2";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "td.zt1 a";
        // 价格规则
        priceRelu = "p:matchesOwn(预算金额)";
        // 内容规则
        fullcontentRelu = "div.c_bodyDiv";
        // 列表url节点规则
        nodeListRelu = "table#node_list tbody tr";
        // 城市代码
        cityIdRelu = 13;
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccgp.shangxi.url").split(",");
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected int getCatId(Document parse) {
        try {
            String text = parse.select(this.catIdRelu).text();
            return getCatIdByText(text);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select(this.priceRelu).get(0).text().replaceAll("预算金额.*：", "");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected Date getAddTime(Element element) {
        Date parse = null;
        try {
            String date = Util.match("\\d+-\\d+-\\d+", element.html())[0];
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            parse = format.parse(date);
        } catch (Exception e) {
            logger.error("获取日期错误：" + e, e);
        }
        return parse;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = null;
        try {
            String href = document.select("a:matchesOwn(后一页)").get(0).attr("href");
            if (!href.startsWith("http")) {
                href = "http://www.ccgp-shanxi.gov.cn/" + href;
            }
            nextPageUrl = href;
        } catch (Exception ignore) {
        }
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
            url = "http://www.ccgp-shanxi.gov.cn/" + href;
        } else {
            url = href;
        }
        return url;
    }
}
