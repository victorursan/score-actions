package io.cloudslang.content.database.services.dbconnection;

import com.mchange.v2.c3p0.PooledDataSource;
import io.cloudslang.content.database.services.dbconnection.PooledDataSourceCleaner.STATE_CLEANER;
import io.cloudslang.content.database.utils.TripleDES;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;


/**
 * The class will get connection or close connection for the SQL.java. If the
 * connection pooling is enabled, it manages the pooled datasource. DataSource is
 * provided by PooledDataSourceProvider. At this moment, we only have
 * C3P0PooledDataSourceProvider which will provide pooled DataSource. Each
 * DataSource pools the connection per user base. This manager will also control
 * the total max pool size per dbms base. Each entry in dbmsTable represent a dbms
 * by dbType.dbUrl. For each dbms, it has a datasource table which has DataSources
 * provided by the PooledDataSourceProvider. The configuration for pooled DataSource
 * and the connection pooling, is in
 * //<ooHome>//RAS//Java//Default//webapp//conf//databasePooling.properties
 * <p/>
 * There is low priority thread which to check the dbmsPoolTable to clean the
 * the empty DataSource or dbmsPool if the datasource table is empty.
 *
 * @author ggu
 */
public class DBConnectionManager {
    //logging
//    protected static final Log logger = LogFactory.getLog(DBConnectionManager.class);

    //singleton instance, make it protected so it can be mocked
    protected static volatile DBConnectionManager instance = null;

    //table to hole the providers, for now it only has c3p0 provider
    protected Hashtable<String, PooledDataSourceProvider> providerTable = null;

    //dbms pool table, key = dbType + "." + dbUrl
    //the reason that keep the dbType is we might use this dbType to find what
    //datasource provider later if we have different kinds of datasource provider
    //right now we only have one provider c3p0. Other provider might be implemented
    //if it is necessary.
    //the dbms pool table will have Hashtable dsTable which contians DataSources
    //the key for the dsTable is dbUrl + "." + username + "." + encryptedpassword
    //hashtable is synchronized already
    protected Hashtable<String, Hashtable<String, DataSource>> dbmsPoolTable = null;

    //datasource cleaner to clean the datasource with 0 connections.
    private PooledDataSourceCleaner datasourceCleaner = null;

    //the thread that will run the cleaner runnable
    private Thread cleanerThread = null;

    //properties that contain configurable connection pooling params
    protected Properties dbPoolingProperties = null;

    //property that will check when to clean the datasource table
    private static final String DB_DATASOURCE_CLEAN_INTERNAL_NAME =
            "db.datasource.clean.interval";
    //2 hours in seconds
    private static final String DB_DATASOURCE_CLEAN_INTERNAL_DEFAULT_VALUE = "7200";//2 * 60 * 60;

    //proprety that will decide if we want to have pooling enabled
    private static final String DB_POOL_ENABLE_NAME = "db.pooling.enable";

    //default is false, meaning if the databasePooling.properties is not
    //there or the property is not there, then we don't want to have pooling
    private static final String DB_POOL_ENABLE_DEFAULT_VALUE = "false";

    //max number of connections for db server, this will control all the pooled
    //datasources for same db server.
    private final static String MAX_TOTAL_POOL_SIZE_DEFAULT_VALUE = "100";

    //properties in databasePooling.properties which are specific for
    //dbtype, this will control total max pool size for dbms
    //oracle
    private final static String ORACLE_MAX_POOL_SIZE_NAME =
            "oracle.connection.total.maxpoolsize";
    //sql server
    private final static String MSSQL_MAX_POOL_SIZE_NAME =
            "mssql.connection.total.maxpoolsize";
    //mysql
    private final static String MYSQL_MAX_POOL_SIZE_NAME =
            "mysql.connection.total.maxpoolsize";
    //db2
    private final static String DB2_MAX_POOL_SIZE_NAME =
            "db2.connection.total.maxpoolsize";

    //sybase
    private final static String SYBASE_MAX_POOL_SIZE_NAME =
            "sybase.connection.total.maxpoolsize";
    //netcool
    private final static String NETCOOL_MAX_POOL_SIZE_NAME =
            "netcool.connection.total.maxpoolsize";

    //custom
    private final static String CUSTOM_MAX_POOL_SIZE_NAME =
            "custom.connection.total.maxpoolsize";

    public static enum DBType {
        ORACLE, MSSQL, SYBASE, NETCOOL, DB2, MYSQL, POSTGRESQL, CUSTOM
    }


    /**
     * if the pooling is enabled or not, default is false
     */
    protected boolean isPoolingEnabled = false;

    /**
     * constructor
     */
    protected DBConnectionManager() {
		/* no longer supported in 10x:		
			//load configurable properties
			//need to get configurable db properties 		 
			if(dbPoolingProperties == null)
			{
				dbPoolingProperties = this.loadDbPoolingProperties();
			}
		*/

    }

    /**
     * @return singleton instance of this manager
     */
    public static DBConnectionManager getInstance() {
        if (instance == null) {
            synchronized (DBConnectionManager.class) {
                if (instance == null) {
                    instance = new DBConnectionManager();
                }
            }
        }
        return instance;
    }

    private void customizeDBConnectionManager(Properties dbPoolingProperties) {
        customizeDbPoolingProperties(dbPoolingProperties);
        if (isPoolingEnabled) {
            createPoolTable();
            createCleaner();
        }
    }

    private void customizeDbPoolingProperties(Properties dbPoolingProperties) {
        if (dbPoolingProperties != null && dbPoolingProperties.size() > 0) {
            this.dbPoolingProperties = dbPoolingProperties;
            this.isPoolingEnabled = this.getPropBooleanValue(DB_POOL_ENABLE_NAME,
                    DB_POOL_ENABLE_DEFAULT_VALUE);
        }
    }

    private void createPoolTable() {
        if (dbmsPoolTable == null) {
            dbmsPoolTable = new Hashtable<>();
        }
    }

    /**
     * destructor
     */
    public void finalize() throws Exception {
        if (this.isPoolingEnabled) {
            //will clean up everything, dbmsPoolTable, datasourceCleaner,
            //cleanerThread
            this.shutdownDbmsPools();
        }
    }

    /**
     * @param aDbType   one of the supported db type, for example ORACLE, NETCOOL
     * @param aDbUrl    connection url
     * @param aUsername username to connect to db
     * @param aPassword password to connect to db
     * @return a Connection to db
     * @throws SQLException
     */
    public synchronized Connection getConnection(DBType aDbType,
                                                 String aDbUrl,
                                                 String aUsername,
                                                 String aPassword,
                                                 Properties properties)
            throws SQLException {
        if (aDbUrl == null || aDbUrl.length() == 0) {
            throw new SQLException
                    ("Failed to check out connection dbUrl is empty");
        }

        if (aUsername == null || aUsername.length() == 0) {
            String msg =
                    "Failed to check out connection,username is empty. dburl = " + aDbUrl;

            throw new SQLException(msg);
        }

        if (aPassword == null || aPassword.length() == 0) {
            String msg =
                    "Failed to check out connection, password is empty. username = "
                            + aUsername + " dbUrl = " + aDbUrl;

            throw new SQLException(msg);
        }

        customizeDBConnectionManager(properties);
        Connection retCon;

        if (!this.isPoolingEnabled) {
            //just call driver manager to create connection
            retCon = this.getPlainConnection(aDbUrl, aUsername, aPassword);
        } else //want connection pooling
        {
            if (aDbType == null) {
                throw new SQLException
                        ("Failed to check out connection db type is null");
            }

            //if the runnable has been shutdown when dbmspoolsize is 0
            //then need to resumbit to the thread and start it again
            if (datasourceCleaner.getState() == STATE_CLEANER.SHUTDOWN) {
//               todo logger.info("datasourceCleaner was shutdowned, will restart it");
                //submit it to the thread to run
                cleanerThread = new Thread(datasourceCleaner);
                cleanerThread.setPriority(Thread.MIN_PRIORITY);
                cleanerThread.start();
            }

            //will use pooled datasource provider
            retCon = this.getPooledConnection(aDbType, aDbUrl, aUsername, aPassword);
        }

        return retCon;
    }

    /**
     * clean any empty datasource and pool in the dbmsPool table.
     */
    public void cleanDataSources() {
        // todo tracing
//        if (logger.isDebugEnabled()) {
//            logger.debug("Ready to clean pools...");
//        }

        Hashtable<String, ArrayList<String>> removedDsKeyTable = null;

        //gather all the empty ds's key, can't remove item while iterate
        Enumeration<String> allPoolKeys = dbmsPoolTable.keys();
        while (allPoolKeys.hasMoreElements()) {
            String dbPoolKey = allPoolKeys.nextElement();
            Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbPoolKey);
            Enumeration<String> allDsKeys = dsTable.keys();
            while (allDsKeys.hasMoreElements()) {
                String dsKey = allDsKeys.nextElement();
                DataSource ds = dsTable.get(dsKey);

                //c3p0 impl
                if (ds != null && ds instanceof PooledDataSource) {
                    PooledDataSource pDs = (PooledDataSource) ds;
                    int conCount = -1;
                    try {
                        conCount = pDs.getNumConnectionsAllUsers();
                    } catch (SQLException e) {
//                  todo      logger.error
//                                ("Failed to get total number of connections for datasource. dbmsPoolKey = "
//                                        + dbPoolKey, e);
                        continue;
                    }
                    //no connections
                    if (conCount == 0) {
                        ArrayList<String> removedList = null;
                        if (removedDsKeyTable == null) {
                            removedDsKeyTable = new Hashtable<String, ArrayList<String>>();
                        } else {
                            removedList = removedDsKeyTable.get(dbPoolKey);
                        }

                        if (removedList == null) {
                            removedList = new ArrayList<String>();
                            removedList.add(dsKey);
                            removedDsKeyTable.put(dbPoolKey, removedList);
                        } else {
                            removedList.add(dsKey);
                        }
                    }
                }
            }
        }

        //have empty ds
        if (removedDsKeyTable != null && !removedDsKeyTable.isEmpty()) {
            Enumeration<String> removedPoolKeys = removedDsKeyTable.keys();
            while (removedPoolKeys.hasMoreElements()) {
                String removedPoolKey = removedPoolKeys.nextElement();
                PooledDataSourceProvider provider = this.getProvider(removedPoolKey);
                ArrayList<String> removedDsList = removedDsKeyTable.get(removedPoolKey);
                Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(removedPoolKey);
                Iterator<String> it = removedDsList.iterator();
                while (it.hasNext()) {
                    String dsKey = it.next();
                    DataSource removedDs = dsTable.remove(dsKey);
                    try {
                        provider.closePooledDataSource(removedDs);
                    } catch (SQLException e) {
                        //can't show the dsKey since it has encrypted password there
//                  todo      logger.error("Failed to close datadsource in dmbs poolKey = "
//                                + removedPoolKey, e);
                        continue;
                    }

                    //tracing
//                todo    if (logger.isDebugEnabled()) {
//                        logger.debug("Removed one datasource in dbms poolKey = "
//                                + removedPoolKey);
//                    }
                }
                //don't have any ds for the pool key
                if (dsTable.isEmpty()) {
                    dsTable = null;
                    dbmsPoolTable.remove(removedPoolKey);
                    //tracing
//              todo      if (logger.isDebugEnabled()) {
//                        logger.debug("Removed dbms poolKey = " + removedPoolKey);
//                    }
                }
            }
        }
    }

    /**
     * force shutdown everything
     */
    public synchronized void shutdownDbmsPools() {
        //force shutdown
        //runnable
        datasourceCleaner.shutdown();
        datasourceCleaner = null;
        //shell for the runnable
        cleanerThread.interrupt();//stop the thread
        cleanerThread = null;

        if (dbmsPoolTable == null) {
            return;
        }
        Enumeration<String> allDbmsKeys = dbmsPoolTable.keys();
        while (allDbmsKeys.hasMoreElements()) {
            String dbmsKey = allDbmsKeys.nextElement();
            PooledDataSourceProvider provider = this.getProvider(dbmsKey);
            Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbmsKey);
            for (DataSource ds : dsTable.values()) {
                try {
                    provider.closePooledDataSource(ds);
                } catch (SQLException e) {
//          todo          logger.error("Failed to close datasource in dbms poolKey = "
//                            + dbmsKey);
                }
            }
            dsTable.clear();
        }
        dbmsPoolTable.clear();
        dbmsPoolTable = null;
    }

    /**
     * load the properties from databasePooling.properties
     * @return the Properties contain the name=value pair from databasePooling.properties
     */
	/* no longer supported in 10x
	protected Properties loadDbPoolingProperties()
	{
		Properties retProp = null;
		
		String ooHome = null; 
		
		//enhancement for k2 so we can move on with no ICONCLUDEHOME or RAS dir
		try
		{
			ooHome = HomeUtil.getIconcludeHome();
			//try one more time, for ras installation only
			if(ooHome == null)
			{
				//get the path of this class , then figure out what is the oo or ras
				//installation
				String pathOfMan = ClassUtil.getLoadedFromPath(DBConnectionManager.class);
				int rasPos = pathOfMan.indexOf("RAS");
				ooHome = pathOfMan.substring(0,rasPos - 1);
			}
		}catch(Exception e)
		{
			//could get exception where there is no iconclude home
			//tracing
			if(logger.isDebugEnabled())
			{
				logger.debug("Won't be able to get ICONCLUDE_HOME or RAS path," + e);
			}
			
			return null;
		}
		
		//tracing
		if(logger.isDebugEnabled())
		{
			logger.debug("ooHome = " + ooHome);
		}
		
		StringBuffer buff = new StringBuffer(ooHome);
		buff.append(File.separator);
		buff.append("RAS");
		buff.append(File.separator);
		buff.append("Java");
		buff.append(File.separator);
		buff.append("Default");
		buff.append(File.separator);
		buff.append("webapp");
		buff.append(File.separator);
		buff.append("conf");
		buff.append(File.separator);
		buff.append("databasePooling.properties");
		//<ooHome>//RAS//Java//Default//webapp//conf//databasePooling.properties
		String jrasPropertiesFilePath = buff.toString();
		
		retProp = new Properties();
		FileInputStream in;
		try 
		{
			in = new FileInputStream(jrasPropertiesFilePath);
			if(in != null)
			{
				retProp.load(in);
				in.close();
			}
		} catch (FileNotFoundException e) 
		{
			retProp = null;
			logger.warn("Faled to load databasePooling properties, will not have connections pooling.", e);
		}
		catch (IOException e) 
		{ 
			logger.warn("Faled to load databasePooling properties, will not have connections pooling.", e);
			retProp = null;
		}
		
		return retProp;
	}*/

    /**
     * get boolean value based on the property name from property file
     * the property file is databasePooling.properties
     *
     * @param aPropName     a property name
     * @param aDefaultValue a default value for that property, if the property is not there.
     * @return boolean value of that property
     */
    protected boolean getPropBooleanValue(String aPropName, String aDefaultValue) {
        boolean retValue = false;

        String temp = dbPoolingProperties.getProperty(aPropName, aDefaultValue);
        retValue = Boolean.valueOf(temp);

        //tracing
//     todo   if (logger.isDebugEnabled()) {
//            logger.debug("property name =  " + aPropName + " value = " + retValue);
//        }
        return retValue;
    }


    /**
     * get int value based on the property name from property file
     * the property file is databasePooling.properties
     *
     * @param aPropName     a property name
     * @param aDefaultValue a default value for that property, if the property is not there.
     * @return int value of that property
     */
    protected int getPropIntValue(String aPropName, String aDefaultValue) {
        int retValue = -1;

        String temp = dbPoolingProperties.getProperty(aPropName, aDefaultValue);
        retValue = Integer.valueOf(temp);

        //tracing
//  todo      if (logger.isDebugEnabled()) {
//            logger.debug("property name =  " + aPropName + " value = " + retValue);
//        }
        return retValue;
    }

    /**
     * create and start a pool cleaner if pooling is enabled.
     */
    private void createCleaner() {
        if (cleanerThread == null) {
            int interval = getPropIntValue
                    (DB_DATASOURCE_CLEAN_INTERNAL_NAME,
                            DB_DATASOURCE_CLEAN_INTERNAL_DEFAULT_VALUE);

            //tracing
//       todo     if (logger.isDebugEnabled()) {
//                logger.debug("PooledDataSource cleaner interval = " + interval);
//            }

            //this runnable
            this.datasourceCleaner = new PooledDataSourceCleaner(this, interval);
            //submit it to the thread to run
            this.cleanerThread = new Thread(datasourceCleaner);
            this.cleanerThread.setDaemon(true);
            this.cleanerThread.start();
        }
    }

    /**
     * @param aDbmsPoolKey a key to find the datasource table
     * @return a PooledDataSourceProvider
     */
    private PooledDataSourceProvider getProvider(String aDbmsPoolKey) {
        PooledDataSourceProvider retProvider = null;
        //TODO: now we only has one provider, later should use that key to find
        //what type of db and find provider since first part of that key is dbType
        String providerName = C3P0PooledDataSourceProvider.C3P0_DATASOURCE_PROVIDER_NAME;

        retProvider = providerTable.get(providerName);

        return retProvider;
    }

    /**
     * @param aDbUrl    connection url
     * @param aUsername username to connect to db
     * @param aPassword password to connect to db
     * @return a db Connection implementation from whatever the driver manager
     * supplies
     * @throws SQLException
     */
    protected Connection getPlainConnection(String aDbUrl,
                                            String aUsername,
                                            String aPassword)
            throws SQLException {
        //tracing
//      todo  if (logger.isDebugEnabled()) {
//            logger.debug("Will getPlainConnection , dbUrl = " + aDbUrl +
//                    " username = " + aUsername);
//        }
        Connection retCon = DriverManager.getConnection(aDbUrl, aUsername, aPassword);

        //tracing
//     todo   if (logger.isDebugEnabled()) {
//            logger.debug("Done getPlainConnection , dbUrl = " + aDbUrl
//                    + " username = " + aUsername);
//        }
        return retCon;
    }

    /**
     * @param aDbType   one of the supported db type, for example ORACLE, NETCOOL
     * @param aDbUrl    connection url
     * @param aUsername username to connect to db
     * @param aPassword password to connect to db
     * @return a db Connection which is pooled
     * @throws SQLException
     */
    protected Connection getPooledConnection(DBType aDbType,
                                             String aDbUrl,
                                             String aUsername,
                                             String aPassword)
            throws SQLException {
        Connection retCon = null;

        //key to hashtable of datasources for that dbms
        String dbmsKey = aDbType + "." + aDbUrl;

        //tracing
//      todo  if (logger.isDebugEnabled()) {
//            logger.debug("Will getPooledConnection , dbms key = " + dbmsKey +
//                    " username = " + aUsername);
//        }

        if (dbmsPoolTable.containsKey(dbmsKey)) {
            //tracing
//      todo      if (logger.isDebugEnabled()) {
//                logger.debug("Find dbms pool table with key = " + dbmsKey);
//            }

            //each pool has pooled datasources, pool is based on dbUrl
            //so we can control the total size of connection to dbms
            Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbmsKey);

            String encryptedPass = null;
            try {
                encryptedPass = TripleDES.encryptPassword(aPassword);
            } catch (Exception e) {
                throw new SQLException
                        ("Failed to encrypt password for key = " + dbmsKey, e);
            }

            String dsTableKey = aDbUrl + "." + aUsername + "." + encryptedPass;

            DataSource ds = null;
            if (dsTable.containsKey(dsTableKey)) {
                //tracing
//    todo            if (logger.isDebugEnabled()) {
//                    logger.debug("Find datasource with key = " + aDbUrl + "." + aUsername + ".<password>");
//                }

                ds = dsTable.get(dsTableKey);
                retCon = ds.getConnection();
            } else {
                //tracing
//         todo       if (logger.isDebugEnabled()) {
//                    logger.debug("No datasource with key = " + aDbUrl + "."
//                            + aUsername + "<password>, will create one");
//                }

                //need to check if it is ok to create another ds
                ds = this.createDataSource(aDbType, aDbUrl, aUsername, aPassword, dsTable);
                retCon = ds.getConnection();

                //tracing
//           todo     if (logger.isDebugEnabled()) {
//                    logger.debug("Put the created DataSource into dsTable , dbms key = " + dbmsKey +
//                            " dsTableKey = " + aDbUrl + "." + aUsername + ".<password>");
//                }
                dsTable.put(dsTableKey, ds);
            }
        } else//don't have dbmsKey, will create one for that dbtype.dburl
        {
            //tracing
//     todo       if (logger.isDebugEnabled()) {
//                logger.debug
//                        ("No dbms pool table with dbmsKey = " + dbmsKey + " will create a one");
//            }

            //just create, don't need to check, since we don't have this dbmsKey
            PooledDataSource ds = (PooledDataSource)this.createDataSource(aDbType, aDbUrl, aUsername, aPassword);
            retCon = getPooledConnection(ds, aUsername, aPassword);

            Hashtable<String, DataSource> dsTable = new Hashtable<String, DataSource>();
            String encryptedPass = null;
            try {
                encryptedPass = TripleDES.encryptPassword(aPassword);
            } catch (Exception e) {
                throw new SQLException
                        ("Failed to encrypt password for key = " + dbmsKey, e);
            }
            String dsTableKey = aDbUrl + "." + aUsername + "." + encryptedPass;

            //tracing
//      todo      if (logger.isDebugEnabled()) {
//                logger.debug("Put the created DataSource into dsTable , dbms key = " + dbmsKey +
//                        " dsTableKey = " + aDbUrl + "." + aUsername + ".<password>");
//            }

            dsTable.put(dsTableKey, ds);

            //tracing
//            if (logger.isDebugEnabled()) {
//         todo       logger.debug("Put the dsTable into dbms table dbms key = " + dbmsKey);
//            }
            dbmsPoolTable.put(dbmsKey, dsTable);
        }

        //tracing
//     todo   if (logger.isDebugEnabled()) {
//            logger.debug("Done getPooledConnection , dbms key = " + dbmsKey + " username = " + aUsername);
//        }

        return retCon;
    }

    private Connection getPooledConnection(PooledDataSource ds, String aUsername, String aPassword) throws SQLException {
        Connection retCon;
        try {
             retCon = ds.getConnection();
        } catch (Exception e) {
            if (ds.sampleLastAcquisitionFailureStackTrace(aUsername, aPassword) != null) {
                throw new SQLException(ds.sampleLastAcquisitionFailureStackTrace(aUsername, aPassword));
            } else if (ds.sampleLastCheckinFailureStackTrace(aUsername, aPassword) != null) {
                throw new SQLException(ds.sampleLastCheckinFailureStackTrace(aUsername, aPassword));
            } else if (ds.sampleLastCheckoutFailureStackTrace(aUsername, aPassword) != null) {
                throw new SQLException(ds.sampleLastCheckoutFailureStackTrace(aUsername, aPassword));
            } else if (ds.sampleLastConnectionTestFailureStackTrace(aUsername, aPassword) != null) {
                throw new SQLException(ds.sampleLastConnectionTestFailureStackTrace(aUsername, aPassword));
            } else if (ds.sampleLastIdleTestFailureStackTrace(aUsername, aPassword) != null) {
                throw new SQLException(ds.sampleLastIdleTestFailureStackTrace(aUsername, aPassword));
            } else {
                throw new SQLException(e);
            }
        }
        return retCon;
    }

    /**
     * create a new pooled datasource if total max pool size for the dbms is still
     * ok. total max pool size per user <= total max pool size specified for
     * specific db type.
     * for example, max pool size per user = 20, total max pool size for oracle
     * dbms is 100. only 5 datasource can be opened.
     *
     * @param aDbType   one of the supported db type, for example ORACLE, NETCOOL
     * @param aDbUrl    connection url
     * @param aUsername username to connect to db
     * @param aPassword password to connect to db
     * @param aDsTable  is used to check if total max pool size for that dbms exceed
     * @return a pooled datasource
     * @throws SQLException
     */
    protected DataSource createDataSource(DBType aDbType,
                                          String aDbUrl,
                                          String aUsername,
                                          String aPassword,
                                          Hashtable<String, DataSource> aDsTable)
            throws SQLException {
        DataSource retDatasource = null;

        String totalMaxPoolSizeName = null;

        switch (aDbType) {
            case ORACLE:
                totalMaxPoolSizeName = ORACLE_MAX_POOL_SIZE_NAME;
                break;
            case MSSQL:
                totalMaxPoolSizeName = MSSQL_MAX_POOL_SIZE_NAME;
                break;
            case MYSQL:
                totalMaxPoolSizeName = MYSQL_MAX_POOL_SIZE_NAME;
                break;
            case SYBASE:
                totalMaxPoolSizeName = SYBASE_MAX_POOL_SIZE_NAME;
                break;
            case DB2:
                totalMaxPoolSizeName = DB2_MAX_POOL_SIZE_NAME;
                break;
            case NETCOOL:
                totalMaxPoolSizeName = NETCOOL_MAX_POOL_SIZE_NAME;
                break;
            default:
                totalMaxPoolSizeName = CUSTOM_MAX_POOL_SIZE_NAME;
                break;
        }

        int totalMaxPoolSize = this.getPropIntValue(totalMaxPoolSizeName,
                MAX_TOTAL_POOL_SIZE_DEFAULT_VALUE);
        int perUserMaxPoolSize =
                this.getPropIntValue(PooledDataSourceProvider.MAX_POOL_SIZE_NAME,
                        PooledDataSourceProvider.MAX_POOL_SIZE_DEFAULT_VALUE);

        int numDs = aDsTable.size();

        int actualTotal = perUserMaxPoolSize * numDs;

        if (actualTotal >= totalMaxPoolSize) {
            throw new TotalMaxPoolSizeExceedException
                    ("Can not create another datasource, the total max pool size exceeded. " +
                            " Expected total max pool size = " + totalMaxPoolSize +
                            " Actual total max pool size = " + actualTotal);
        }

        retDatasource = this.createDataSource(aDbType, aDbUrl, aUsername, aPassword);

        return retDatasource;
    }

    /**
     * @param aDbType   one of the supported db type, for example ORACLE, NETCOOL
     * @param aDbUrl    connection url
     * @param aUsername username to connect to db
     * @param aPassword password to connect to db
     * @return a pooled datasource
     * @throws SQLException
     */
    private DataSource createDataSource(DBType aDbType,
                                        String aDbUrl,
                                        String aUsername,
                                        String aPassword)
            throws SQLException {
        DataSource retDatasource = null;
        PooledDataSourceProvider provider = null;

        //tracing
//     todo   if (logger.isDebugEnabled()) {
//            logger.debug
//                    ("Will create datasource with key = " + aDbUrl + "." + aUsername + ".<password>");
//        }

        if (providerTable == null) {
            //tracing
//      todo      if (logger.isDebugEnabled()) {
//                logger.debug("Will create provider for dbType = " + aDbType);
//            }

            switch (aDbType) {
                //only has one at the moment
                default:
                    provider = new C3P0PooledDataSourceProvider(dbPoolingProperties);
            }
            String name = provider.getProviderName();
            providerTable = new Hashtable<String, PooledDataSourceProvider>();
            providerTable.put(name, provider);

            //tracing
//       todo     if (logger.isDebugEnabled()) {
//                logger.debug("Created a provider for dbType = " + aDbType +
//                        " provider name = " + name);
//            }
        }

        String providerName = null;
        switch (aDbType) {
            default:
                providerName = C3P0PooledDataSourceProvider.C3P0_DATASOURCE_PROVIDER_NAME;
        }

        provider = providerTable.get(providerName);

        //tracing
//     todo   if (logger.isDebugEnabled()) {
//            logger.debug("Will use a provider for dbType = " + aDbType +
//                    " provider name = " + providerName);
//        }

        retDatasource = provider.openPooledDataSource(aDbType,
                aDbUrl,
                aUsername,
                aPassword);

        //tracing
//      todo  if (logger.isDebugEnabled()) {
//            logger.debug
//                    ("Done creating datasource with key = " + aDbUrl + "." + aUsername + ".<password>");
//        }

        return retDatasource;
    }

    //The followings are only for testing purpose

    /**
     * return how many dbms pools
     */
    public int getDbmsPoolSize() {
        return dbmsPoolTable.size();
    }

    /**
     * @param aDbType type of db
     * @param aDbUrl  a connection url string
     * @return how many connections for dbms
     */
    public int getConnectionSize(DBType aDbType, String aDbUrl)
            throws SQLException {
        int retTotal = 0;

        String dbmsPoolKey = aDbType + "." + aDbUrl;
        Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbmsPoolKey);
        PooledDataSourceProvider provider = this.getProvider(dbmsPoolKey);

        if (dsTable != null) {
            for (DataSource ds : dsTable.values()) {
                retTotal = retTotal + provider.getAllConnectionNumber(ds);
            }
        }

        return retTotal;

    }

    /**
     * @param aDbType a db type
     * @param aDbUrl  a connection url
     * @return how many check out connections for dbms
     */
    public int getCheckedOutConnectionSize(DBType aDbType, String aDbUrl)
            throws SQLException {
        int retTotal = 0;

        String dbmsPoolKey = aDbType + "." + aDbUrl;
        Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbmsPoolKey);
        PooledDataSourceProvider provider = this.getProvider(dbmsPoolKey);

        if (dsTable != null) {
            for (DataSource ds : dsTable.values()) {
                retTotal = retTotal + provider.getCheckedOutConnectionNumber(ds);
            }
        }

        return retTotal;
    }

    /**
     * @param aDbType a db type
     * @param aDbUrl  a connection url
     * @return how many check in connections for dbms
     */
    public int getCheckedInConnectionSize(DBType aDbType, String aDbUrl)
            throws SQLException {
        int retTotal = 0;

        String dbmsPoolKey = aDbType + "." + aDbUrl;
        Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbmsPoolKey);
        PooledDataSourceProvider provider = this.getProvider(dbmsPoolKey);

        if (dsTable != null) {
            for (DataSource ds : dsTable.values()) {
                retTotal = retTotal + provider.getCheckedInConnectionNumber(ds);
            }
        }

        return retTotal;

    }

    /**
     * @return all the connections in for all dbms
     */
    public int getTotalConnectionSize() throws SQLException {
        int retTotal = 0;

        Enumeration<String> allDbmsKeys = dbmsPoolTable.keys();
        while (allDbmsKeys.hasMoreElements()) {
            String dbmsPoolKey = allDbmsKeys.nextElement();
            Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbmsPoolKey);
            PooledDataSourceProvider provider = this.getProvider(dbmsPoolKey);

            if (dsTable != null) {
                for (DataSource ds : dsTable.values()) {
                    retTotal = retTotal + provider.getAllConnectionNumber(ds);
                }
            }
        }

        return retTotal;
    }

    /**
     * @return all the checked out connections in for all dbms
     */
    public int getTotalCheckedOutConnectionSize() throws SQLException {
        int retTotal = 0;

        Enumeration<String> allDbmsKeys = dbmsPoolTable.keys();
        while (allDbmsKeys.hasMoreElements()) {
            String dbmsPoolKey = allDbmsKeys.nextElement();
            Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbmsPoolKey);
            PooledDataSourceProvider provider = this.getProvider(dbmsPoolKey);

            if (dsTable != null) {
                for (DataSource ds : dsTable.values()) {
                    retTotal = retTotal + provider.getCheckedOutConnectionNumber(ds);
                }
            }
        }

        return retTotal;
    }

    /**
     * @return all the checked in connections in for all dbms
     */
    public int getTotalCheckedInConnectionSize() throws SQLException {
        int retTotal = 0;

        Enumeration<String> allDbmsKeys = dbmsPoolTable.keys();
        while (allDbmsKeys.hasMoreElements()) {
            String dbmsPoolKey = allDbmsKeys.nextElement();
            Hashtable<String, DataSource> dsTable = dbmsPoolTable.get(dbmsPoolKey);
            PooledDataSourceProvider provider = this.getProvider(dbmsPoolKey);
            if (dsTable != null) {
                for (DataSource ds : dsTable.values()) {
                    retTotal = retTotal + provider.getCheckedInConnectionNumber(ds);
                }
            }
        }

        return retTotal;
    }

    /**
     * Sets the dataSourceCleaner.
     *
     * @param cleaner
     */
    protected void setDatasourceCleaner(PooledDataSourceCleaner cleaner) {
        this.datasourceCleaner = cleaner;
    }

    /**
     * One line method for retrieving a C3P0PooledDataSourceProvider in order to enable testing with mockito.
     *
     * @return
     */
    protected C3P0PooledDataSourceProvider getC3P0PooledDataSourceProvider() {
        return new C3P0PooledDataSourceProvider(dbPoolingProperties);
    }

}//end DBConnectionManager
