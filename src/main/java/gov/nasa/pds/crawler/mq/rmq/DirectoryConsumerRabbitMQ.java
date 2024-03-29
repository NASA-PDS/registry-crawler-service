package gov.nasa.pds.crawler.mq.rmq;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;

import gov.nasa.pds.crawler.mq.MQPublisher;
import gov.nasa.pds.crawler.proc.DirectoryProcessor;
import gov.nasa.pds.registry.common.mq.msg.CollectionInventoryMessage;
import gov.nasa.pds.registry.common.mq.msg.DirectoryMessage;
import gov.nasa.pds.registry.common.mq.msg.MQConstants;
import gov.nasa.pds.registry.common.mq.msg.ProductMessage;
import gov.nasa.pds.registry.common.util.ExceptionUtils;


/**
 * RabbitMQ consumer to process directory messages
 * @author karpenko
 */
public class DirectoryConsumerRabbitMQ extends DefaultConsumer implements MQPublisher
{
    private Logger log;

    private Gson gson;
    private DirectoryProcessor proc;
    
    
    /**
     * Constructor
     * @param channel RabbitMQ connection channel
     */
    public DirectoryConsumerRabbitMQ(Channel channel)
    {
        super(channel);
        
        log = LogManager.getLogger(this.getClass());
        gson = new Gson();
        proc = new DirectoryProcessor(this);
    }

    
    /**
     * Start consuming messages
     * @throws Exception
     */
    public void start() throws Exception
    {
        getChannel().basicConsume(MQConstants.MQ_DIRS, false, this);
    }
    
    
    /**
     * Handle delivery of a new message from the directory queue
     */
    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, 
            AMQP.BasicProperties properties, byte[] body) throws IOException
    {
        long deliveryTag = envelope.getDeliveryTag();

        DirectoryMessage dirMsg = null;
        
        try
        {
            String jsonStr = new String(body);
            dirMsg = gson.fromJson(jsonStr, DirectoryMessage.class);
        }
        catch(Exception ex)
        {
            log.error("Invalid message", ex);

            // ACK message (delete from the queue)
            getChannel().basicAck(deliveryTag, false);
            return;
        }

        try
        {
            proc.processMessage(dirMsg);
        }
        catch(Exception ex)
        {
            log.error("Could not process message: " + ExceptionUtils.getMessage(ex));
            getChannel().basicReject(deliveryTag, true);
            return;
        }
        
        // ACK message (delete from the queue)
        getChannel().basicAck(deliveryTag, false);
    }


    
    @Override
    public void publish(DirectoryMessage msg) throws Exception
    {
        String jsonStr = gson.toJson(msg);
        getChannel().basicPublish("", MQConstants.MQ_DIRS, 
                MessageProperties.MINIMAL_PERSISTENT_BASIC, jsonStr.getBytes());
    }


    @Override
    public void publish(ProductMessage msg) throws Exception
    {
        String jsonStr = gson.toJson(msg);
        getChannel().basicPublish("", MQConstants.MQ_PRODUCTS, 
                MessageProperties.MINIMAL_PERSISTENT_BASIC, jsonStr.getBytes());
    }


    @Override
    public void publish(CollectionInventoryMessage msg) throws Exception
    {
        String jsonStr = gson.toJson(msg);
        getChannel().basicPublish("", MQConstants.MQ_COLLECTIONS, 
                MessageProperties.MINIMAL_PERSISTENT_BASIC, jsonStr.getBytes());
    }
}
