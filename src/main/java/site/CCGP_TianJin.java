package site;

import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import start.Bidding;
import util.Util;

import java.util.Date;

public class CCGP_TianJin extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_TianJin.class);

    @Override
    protected void setValue() {
        titleRelu = "p font b";
        // 描述规则
        descriptionRelu = "";
        // 采集类型id规则
        catIdRelu = "";
        // 采购人规则
        purchaserRelu = "";
        // 价格规则
        priceRelu = "";
        // 发布时间规则
        addTimeRelu = "span.time";
        // 发布时间匹配规则
        addTimeParse = "yyyy-MM-dd";
        // 内容规则
        detailRelu = "td";
        // 附件规则
        annexRelu = "td div a[target=_blank]";
        // 列表url节点规则
        nodeListRelu = "ul.dataList li";
    }

    @Override
    public void run() {
        // 获取任务url
        setValue();
        String[] urls = Bidding.properties.getProperty("ccpg.tianjin.url").split(",");
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
            url = "http://www.ccgp-tianjin.gov.cn" + href;
        }
        return url;
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        String nextPageUrl = "";
        try {
            if(url.contains("&page=")){
                Integer page = Integer.parseInt(Util.match("&page=(\\d+)", url)[1]);
                nextPageUrl = url.replaceAll("&page=.*","")+"&page="+(page+1);
            }else {
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
            parse = new Date(addTime);
        } catch (Exception e) {
            logger.error("获取日期错误：" + e, e);
        }
        return parse;
    }

    @Override
    protected String getPurchaser(Document parse) {
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
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    @Override
    protected String getAnnex(Document parse) {
        try {
            String href = parse.select(annexRelu).attr("href");
            if(StrUtil.isEmpty(href)){
                href = parse.select("div:containsOwn(附件)+div").select("a").attr("href");
                if(StrUtil.isEmpty(href)){
                    href = parse.select("div:containsOwn(文件下载)+div").select("a").attr("href");
                }
            }
            if(StrUtil.isNotEmpty(href)){
                href = "http://www.ccgp-tianjin.gov.cn" + href;
            }
            return href;
        } catch (Exception e) {
        }
        return null;
    }
}
