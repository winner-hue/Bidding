package site;

import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;
import start.Bidding;
import util.SqlPool;
import util.Util;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_GuiZhou extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_GuiZhou.class);
    private static HashMap<String, String> heads = new HashMap<String, String>();
    private static String relu = "{'cgxqgs': {'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "                 'priceRelu': 'span:containsOwn(预算金额:) + span'},\n" +
            "      'cggg': {\n" +
            "          'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "          'priceRelu': 'span:containsOwn(预算金额:) + span'},\n" +
            "      'gzgg': {'authorRelu': 'label:containsOwn(预算单位：) + div|label:containsOwn(采购人（甲方）：) + div span',\n" +
            "               'priceRelu': 'label:containsOwn(预算金额：) + div|label:containsOwn(合同金额：) + div span',\n" +
            "               'fullcontentRelu': 'div.panel-body'},\n" +
            "      'fbgg': {'authorRelu': 'label:containsOwn(预算单位：) + div|label:containsOwn(采购人（甲方）：) + div span',\n" +
            "               'priceRelu': 'label:containsOwn(预算金额：) + div|label:containsOwn(合同金额：) + div span',\n" +
            "               'fullcontentRelu': 'div.panel-body form-horizontal'},\n" +
            "      'zbcggg': {\n" +
            "          'authorRelu': 'li:has(span:containsOwn(1、采购人信息)) + li span:eq(1)',\n" +
            "          'priceRelu': 'tbody tr:eq(1) td:eq(4)'},\n" +
            "      'dylygs': {\n" +
            "          'authorRelu': 'div.table table tbody tr td:containsOwn(采购单位) + td|label:containsOwn(采购人（甲方）：) + div span',\n" +
            "          'priceRelu': 'label:containsOwn(合同金额：) + div span',\n" +
            "          'fullcontentRelu': 'div.vF_detail_content'},\n" +
            "      'dylycggg': {\n" +
            "          'authorRelu': 'div.table table tbody tr td:containsOwn(采购单位) + td|label:containsOwn(采购人（甲方）：) + div span|p:containsOwn(采购单位名称:) + span',\n" +
            "          'priceRelu': 'div.table table tbody tr td:containsOwn(总中标金额) + td|label:containsOwn(合同金额：) + div span|div.table table tbody tr td:containsOwn(总成交金额) + td|p:containsOwn(采购计划金额（元）:) + span',\n" +
            "          'fullcontentRelu': 'div.divcss5|form#institutionForm5|div#searchPanel'},\n" +
            "      'zgys_1': {\n" +
            "          'authorRelu': 'div.table table tbody tr td:containsOwn(采购单位) + td|label:containsOwn(采购人（甲方）：) + div span|p:containsOwn(采购单位名称:) + span',\n" +
            "          'priceRelu': 'div.table table tbody tr td:containsOwn(总中标金额) + td|label:containsOwn(合同金额：) + div span|div.table table tbody tr td:containsOwn(总成交金额) + td|p:containsOwn(采购计划金额（元）:) + span',\n" +
            "          'fullcontentRelu': 'div.divcss5|form#institutionForm5|div#searchPanel'}\n" +
            "      }";
    public static JSONObject relus = JSONObject.parseObject(relu);

    @Override
    public void run() {
        setValue();
        final String[] urls = {"http://www.ccgp-guizhou.gov.cn/sjbx/cgxqgs/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/cggg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/gzgg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/fbgg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/zbcggg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/dylygs/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/dylycggg/index.html", "http://www.ccgp-guizhou.gov.cn/sjbx/zgys_1/index.html"};
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
        cityIdRelu = 24;
        nodeListRelu = "div.xnrx ul li";
        titleRelu = "h3";
        fullcontentRelu = "div.xnrx";
    }

    @Override
    protected void main(String[] urls) {
        int retryTime = 3;
        SimpleDateFormat format = null;
        try {
            String retryTimes = Bidding.properties.getProperty("download_retry_times");
            retryTime = Integer.parseInt(retryTimes);
        } catch (Exception ignore) {
        }
        try {
            String deadDateParse = Bidding.properties.getProperty("dead.date");
            format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date parse = format.parse(deadDateParse);
            long time = parse.getTime();
            this.deadDate = new Date(time);
        } catch (Exception ignore) {
            try {
                format = new SimpleDateFormat("yyyy-MM-dd");
                this.deadDate = format.parse(Util.getLastMonth(null, 3));
//                this.deadDate = format.parse("2021-03-11");
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        for (String url : urls) {
            logger.info("当前开始url： " + url);
            this.baseUrl = url;
            startRun(retryTime, url, 0);
        }
    }

    @Override
    protected int getMaxPageSize(Document document) {
        return super.getMaxPageSize(document);
    }

    @Override
    protected String getNextPageUrl(Document document, int currentPage, String httpBody, String url) {
        return super.getNextPageUrl(document, currentPage, httpBody, url);
    }

    @Override
    protected void startRun(int retryTime, String url, int currentPage) {
        String httpBody = getHttpBody(retryTime, url);
        if (httpBody == null) {
            logger.error("下载失败， 直接返回为空");
            return;
        }
        Document document = Jsoup.parse(httpBody);
        List<StructData> allResult = getAllResult(document, httpBody);
        for (StructData data : allResult) {
            String tempUrl = data.getArticleurl();
            String pageSource = getHttpBody(retryTime, tempUrl);
            Document parse = Jsoup.parse(pageSource);
            extract(parse, data, pageSource);
        }
        int count = 0;
        for (StructData resultData : allResult) {
            try {
                String sql;
                String tableName = Bidding.properties.getProperty("table.name");
                if (tableName == null) {
                    sql = Util.getInsertSql("fa_article", StructData.class, resultData);
                } else {
                    sql = Util.getInsertSql(tableName, StructData.class, resultData);
                }
                SqlPool.getInstance().getStatement().execute(sql);
                logger.info("当前插入第：" + count + " 条");
                ++count;
            } catch (SQLException e) {
                logger.error("插入数据错误：" + e, e);
            }
        }
        logger.info("插入成功：" + count + " 条");
        //添加下一页
        if ((allResult.size() - count <= allResult.size() - 1) && (allResult.size() > 0)) {
            try {
                try {
                    currentPage = Integer.parseInt(Util.match("index_(\\d+)", url)[1]);
                } catch (Exception ignore) {
                }
                String nextPageUrl = getNextPageUrl(document, currentPage, httpBody, url);
                if (nextPageUrl != null && (!"".equals(nextPageUrl))) {
                    startRun(retryTime, nextPageUrl, (currentPage + 1));
                }
            } catch (Exception e) {
                logger.error("下一页提取错误：" + e, e);
            }
        }
    }

    @Override
    protected void extract(Document parse, StructData data, String pageSource) {
        super.extract(parse, data, pageSource);
    }

    @Override
    protected String getAnnex(Document parse) {
        return super.getAnnex(parse);
    }

    @Override
    protected String getDetail(Document parse) {
        return super.getDetail(parse);
    }

    @Override
    protected String getPrice(Document parse) {
        return super.getPrice(parse);
    }

    @Override
    protected String getAuthor(Document parse) {
        return super.getAuthor(parse);
    }

    @Override
    protected int getCatId(Document parse) {
        return super.getCatId(parse);
    }

    @Override
    protected String getDescription(Document parse) {
        return super.getDescription(parse);
    }

    @Override
    protected String getTitle(Document parse) {
        return super.getTitle(parse);
    }

    @Override
    protected String getUrl(Element element) {
        return super.getUrl(element);
    }

    @Override
    protected Date getAddTime(Element element) {
        return super.getAddTime(element);
    }

    @Override
    protected List<StructData> getAllResult(Document parse, String httpBody) {
        return super.getAllResult(parse, httpBody);
    }
}
