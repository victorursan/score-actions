package io.cloudslang.content.database.services.dbconnection;

/**
 * The low priority thread class will remove pooled datasource from dbmsPoolTable
 * if the datasource has empty connections
 *
 * @author ggu
 */
public class PooledDataSourceCleaner implements Runnable {
    //logging
//    protected static final Log logger = LogFactory.getLog(PooledDataSourceCleaner.class);

    //interval when this cleaner wakes up in seconds
    //this number is configurable
    private long interval = 60 * 60 * 12; // 12 hours

    //DBConnectionPoolManager handle
    private DBConnectionManager manager = null;

    //state to indicate if this runnable is running or shutdown
    public static enum STATE_CLEANER {
        RUNNING, SHUTDOWN
    }

    ;

    private STATE_CLEANER state = STATE_CLEANER.SHUTDOWN;

    /**
     * constructor
     *
     * @param aManager  a ref to DBConnectionManager
     * @param aInterval an interval in seconds for cleaner to run
     */
    PooledDataSourceCleaner(DBConnectionManager aManager, long aInterval) {
        this.manager = aManager;
        //can be configured in databasePooling.properties
        //db.connectionpool.clean.interval
        this.interval = aInterval;
    }

    /**
     * wake up and clean the pools if the pool has empty connection table.
     */
    public void run() {
//    todo    if (logger.isDebugEnabled()) {
//            logger.debug("start running PooledDataSourceCleaner");
//        }

        state = STATE_CLEANER.RUNNING;
        while (state != STATE_CLEANER.SHUTDOWN) {
            try {
                Thread.sleep(interval * 1000);
            } catch (InterruptedException e) {
//              todo  if (logger.isDebugEnabled()) {
//                    logger.debug("Get interrupted, shutdown the PooledDataSourceCleaner");
//                }
                break;//get out if anyone interrupt
            }
            this.manager.cleanDataSources();

            //if no pool at all, going to stop itself
            if (this.manager.getDbmsPoolSize() == 0) {
//             todo   if (logger.isDebugEnabled()) {
//                    logger.debug("Empty pools, shutdown the PooledDataSourceCleaner");
//                }
                state = STATE_CLEANER.SHUTDOWN;//stop spinning
                break; //get out
            }
        }
    }

    /**
     * force shutdown and derefrence manager
     */
    protected void shutdown() {
//    todo    logger.info("Force shutdown the PooledDataSourceCleaner");
        state = STATE_CLEANER.SHUTDOWN;
        this.manager = null;
    }

    /**
     * @return the state of this runnable
     */
    protected STATE_CLEANER getState() {
        return this.state;
    }
}//end PooledDataSourceCleaner class
