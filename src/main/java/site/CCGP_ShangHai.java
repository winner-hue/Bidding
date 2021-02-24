package site;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static util.Download.getHttpBody;

public class CCGP_ShangHai extends WebGeneral{
    private static Logger logger = LoggerFactory.getLogger(CCGP_ShangHai.class);

    @Override
    public void run() {
        setValue();
        int length_num = 6;
        String url_start = "http://www.zfcg.sh.gov.cn/front/search/category";
        String[] urls = new String[length_num];
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String startDate = format.format(new Date(System.currentTimeMillis() - 3 * 30 * 24 * 60 * 60 * 1000L));
        for (int i = 0; i < length_num; i++) {
            String code = "ZcyAnnouncement".concat(String.valueOf(i + 1));
            urls[i] = url_start.concat("&application_jsons&#44utm=sites_group_front.2ef5001f.0.0.f5992140395c11eb9a363987d6b81c96&categoryCode=".concat(code).concat("&pageSize=50&pageNo=1&publishDateBegin=".concat(startDate).concat("&application_jsons")));
        }
        this.main(urls);
        Bidding.cout.decrementAndGet();
    }

    @Override
    protected void setValue() {
    }

    @Override
    protected void main(String[] urls) {
        super.main(urls);
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
