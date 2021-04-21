package site;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import start.Bidding;
import util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/*
该站点请求过快会返回空数据，建议代理， 或者降低请求频率
 */
public class CCGP_TianJin extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_TianJin.class);
    private static JSONObject cats;

    @Override
    protected void setValue() {
        titleRelu = "p font b";
        // 描述规则
        descriptionRelu = "meta[name='ColumnDescription']";

        cityIdRelu = 23;
        // 采集类型id规则
        catIdRelu = "div#crumbs a:eq(1)";
        // 采购人规则
        authorRelu = "div:matchesOwn(1.采购人信息)+div,div.div:matchesOwn(采购人：)";
        // 发布时间规则
        addTimeRelu = "span.time";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd";
        // 内容规则
        fullcontentRelu = "div.pageInner > table";
        // 附件规则
        fjxxurlRelu = "td div a[target=_blank]";
        // 列表url节点规则
        nodeListRelu = "ul.dataList li";
        priceRelu = "div:matchesOwn(预算金额：),div:matchesOwn(预算金额（万元）：)";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccpg.tianjin.url").split(",");
        cats = JSONObject.parseObject(Bidding.properties_cat.getProperty("tianjin_cat"));
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
        String url = null;
        if (!href.startsWith("http")) {
            if (Util.isMatch("viewer.do\\?id=\\d+", href)) {
                String id = Util.match("viewer.do\\?id=(\\d+)", href)[1];
                url = "http://www.ccgp-tianjin.gov.cn/portal/documentView.do?method=view&id=" + id + "&ver=2";
            } else {
                url = "http://www.ccgp-tianjin.gov.cn" + href;
            }
        }
        return url;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            if (url.contains("&page=")) {
                Integer page = Integer.parseInt(Util.match("&page=(\\d+)", url)[1]);
                nextPageUrl = url.replaceAll("&page=.*", "") + "&page=" + (page + 1);
            } else {
                nextPageUrl = url + "&page=2";
            }
        } catch (Exception ignore) {
        }
        return nextPageUrl;
    }

    @Override
    protected Date getAddTime(Element element) {
        Date parse = null;
        try {
            String addTime = element.select(this.addTimeRelu).get(0).text();
            parse = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US).parse(addTime);
        } catch (Exception e) {
            logger.error("获取日期错误：" + e, e);
        }
        return parse;
    }

    @Override
    protected String getAuthor(Document parse) {
        String author = null;
        try {
            author = parse.select(authorRelu).get(0).text().replaceAll("名称：", "");
        } catch (Exception e) {
            author = Util.match("1.采购单位：(.*?)\\n", parse.html())[0];
        }
        if (author.contains("：")){
            author = author.split("：")[1];
        }
        return author;
    }

    @Override
    protected String getPrice(Document parse) {
        try {
            return parse.select(authorRelu).get(0).text().split("：")[1];
        } catch (Exception ignore) {
        }
        return null;
    }

    @Override
    protected int getCatId(Document parse) {
        int cat_id = 0;
        try {
            String text = parse.select(this.catIdRelu).get(0).text();
            cat_id = cats.getIntValue(text.split("\\-")[0]);
            if (cat_id == 0) {
                cat_id = cats.getIntValue(text.split("—")[0]);
            }
        } catch (Exception e) {
            return -1;
        }
        return cat_id;
    }

    @Override
    protected String getDescription(Document parse) {
        try {
            return parse.select(this.titleRelu).get(0).text();
        } catch (Exception e) {
            return "";
        }
    }
}
