package gov.nasa.pds.crawler;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import gov.nasa.pds.crawler.mq.MQClient;
import gov.nasa.pds.crawler.mq.rmq.RabbitMQClient;
import gov.nasa.pds.registry.common.util.ExceptionUtils;
import gov.nasa.pds.crawler.cfg.Configuration;
import gov.nasa.pds.crawler.cfg.ConfigurationReader;
import gov.nasa.pds.crawler.http.MemoryServlet;
import gov.nasa.pds.crawler.http.StatusServlet;
import gov.nasa.pds.crawler.mq.amq.ActiveMQClient;


/**
 * Crawler server
 * @author karpenko
 */
public class CrawlerServer
{
    private Logger log;
    private Configuration cfg;
    private MQClient mqClient;

    /**
     * Constructor
     * @param cfgFilePath configuration file path
     * @throws Exception an exception
     */
    public CrawlerServer(String cfgFilePath) throws Exception
    {
        log = LogManager.getLogger(this.getClass());
        
        // Read configuration file
        File file = new File(cfgFilePath);
        log.info("Reading configuration from " + file.getAbsolutePath());        
        ConfigurationReader cfgReader = new ConfigurationReader();
        cfg = cfgReader.read(file);
        
        mqClient = createMQClient(cfg);
    }
    
    
    private MQClient createMQClient(Configuration cfg) throws Exception
    {
        if(cfg == null || cfg.mqType == null)
        {
            throw new Exception("Invalid configuration. Message server type is not set.");
        }
        
        switch(cfg.mqType)
        {
        case ActiveMQ:
            return new ActiveMQClient(cfg.amqCfg);
        case RabbitMQ:
            return new RabbitMQClient(cfg.rmqCfg);
        }
        
        throw new Exception("Invalid message server type: " + cfg.mqType);
    }


    /**
     * Run the server
     * @return 0 - server started without errors; 1 or greater - there was an error
     */
    public int run()
    {
        try
        {
            // Start embedded web server
            startWebServer(cfg.webPort);
            
            // Start message queue (ActiveMQ or RabbitMQ) client
            mqClient.run();
        }
        catch(Exception ex)
        {
            log.error(ExceptionUtils.getMessage(ex));
            return 1;
        }
        
        return 0;
    }
    
    
    /**
     * Start embedded web server
     * @param port a port to listen for incoming connections
     */
    private void startWebServer(int port) throws Exception
    {
        // Max threads = 10, min threads = 1
        QueuedThreadPool threadPool = new QueuedThreadPool(10, 1);
        Server server = new Server(threadPool);
        
        // HTTP connector
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("0.0.0.0");
        connector.setPort(port);
        server.addConnector(connector);

        // Servlet handler
        ServletHandler handler = new ServletHandler();
        
        // Status servlet
        ServletHolder statusServlet = new ServletHolder(new StatusServlet(mqClient));
        handler.addServletWithMapping(statusServlet, "/");

        // Memory servlet
        handler.addServletWithMapping(MemoryServlet.class, "/memory");
        server.setHandler(handler);
        
        // Start web server
        server.start();
        
        log.info("Started web server on port " + port);
    }

}
