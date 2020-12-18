package start;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.WebGeneral;

public class BiddingStart implements Job {
    private static Logger logger = LoggerFactory.getLogger(BiddingStart.class);

    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        String webSiteClass = Bidding.properties.getProperty("web_site_class");
        for (String classPath : webSiteClass.split(",")) {
            try {
                while (true) {
                    if (Bidding.cout.get() < Bidding.limitThread) {
                        classPath = classPath.trim();
                        Thread tempClass = (WebGeneral) Class.forName(classPath).newInstance();
                        tempClass.start();
                        Bidding.cout.addAndGet(1);
                        break;
                    }
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                logger.error("反射类发生错误：" + e, e);
            }
        }
    }
}