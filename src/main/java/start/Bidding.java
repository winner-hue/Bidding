package start;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Util;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class Bidding {
    private static Logger logger = LoggerFactory.getLogger(Bidding.class);
    public static Properties properties;
    public static Properties properties_cat;
    public static AtomicInteger cout = new AtomicInteger();
    public static int limitThread = 5;

    static {
        properties = Util.getProps("/ccgp.properties");
        properties_cat = Util.getProps("/cat_mapping.properties");
    }

    public static void main(String[] args) {
        try {
            String timerConfig = properties.getProperty("timer_config");
            String limitThread = properties.getProperty("limit_thread");
            Bidding.limitThread = Integer.parseInt(limitThread);
            if (timerConfig == null) {
                timerConfig = "0 * * * * ?";
            }
            JobDetail jobDetail = JobBuilder.newJob(BiddingStart.class).storeDurably().build();
            Trigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(timerConfig)).build();
            SchedulerFactory factory = new StdSchedulerFactory();
            Scheduler scheduler = factory.getScheduler();
            scheduler.scheduleJob(jobDetail, trigger);
            scheduler.start();
        } catch (Exception e) {
            logger.error("定时器启动错误：" + e, e);
            logger.error("退出系统。。。");
            System.exit(1);
        }
    }
}


