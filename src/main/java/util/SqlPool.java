package util;

import com.alibaba.druid.pool.DruidDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import start.Bidding;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SqlPool {
    private static Logger logger = LoggerFactory.getLogger(SqlPool.class);
    private static SqlPool pool = null;
    private Statement statement;
    private Connection connection;

    public Statement getStatement() {
        return statement;
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    private SqlPool() {
        String driver = Bidding.properties.getProperty("driver");
        String url = Bidding.properties.getProperty("url");
        String user = Bidding.properties.getProperty("user");
        String password = Bidding.properties.getProperty("password");
        int initialSize = Integer.parseInt(Bidding.properties.getProperty("initial_size", "5"));
        int maxActive = Integer.parseInt(Bidding.properties.getProperty("max_active", "30"));
        int minIdle = Integer.parseInt(Bidding.properties.getProperty("min_idle", "3"));
        int maxWait = Integer.parseInt(Bidding.properties.getProperty("max_wait", "5"));
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(url);
        dataSource.setDriverClassName(driver); //这个可以缺省的，会根据url自动识别
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        dataSource.setInitialSize(initialSize);  //初始连接数，默认0
        dataSource.setMaxActive(maxActive);  //最大连接数，默认8
        dataSource.setMinIdle(minIdle);  //最小闲置数
        dataSource.setMaxWait(maxWait * 1000);  //获取连接的最大等待时间，单位毫秒
        dataSource.setPoolPreparedStatements(true); //缓存PreparedStatement，默认false
        dataSource.setMaxOpenPreparedStatements(20);
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();
        } catch (SQLException e) {
            logger.error("链接数据库失败：" + e, e);
            System.exit(1);
        }
    }

    public synchronized static SqlPool getInstance() {
        if (null == pool) {
            pool = new SqlPool();
        }
        return pool;
    }

    public static void main(String[] args) {
        SqlPool.getInstance();
    }
}
