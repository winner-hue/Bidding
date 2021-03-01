package site;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import po.StructData;

import java.util.Date;
import java.util.List;

public class CCGP_ShanDong extends WebGeneral {
    private static Logger logger = LoggerFactory.getLogger(CCGP_ShanDong.class);

    // {'省意向公开': '2500', '省采购公告': '0301', '单一来源公示': '2102', '信息更正': '0305', '省结果公告': '0302', '废标公告': '0306', '省合同公开': '2502', '省验收公开': '2503'}
    @Override
    public void run() {
        super.run();
    }

    @Override
    protected void setValue() {
        super.setValue();
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
        super.startRun(retryTime, url, currentPage);
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
