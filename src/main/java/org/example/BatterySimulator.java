package org.example;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.logging.Logger;

public class BatterySimulator {

    private static final Logger logger = Logger.getLogger(BatterySimulator.class.getName());

    private static Battery battery;

    private static Connection activeMqConnection;

    private static Session activeMqSession;

    private static MessageConsumer activeMqConsumer;

    private static boolean keepAlive = true;

    public static void main(String[] strings) {
        logger.info("org.example.BatterySimulator started...");

        battery = new Battery(100L, 0);

        establishActiveMqConnection();

        try {
            while (keepAlive) {
                Thread.sleep(500);

                TextMessage message = (TextMessage) activeMqConsumer.receive();

                if (message == null)
                    continue;

                String messageBody = message.getText();

                logger.info("messageBody = " + messageBody);

                handleMessage(messageBody);
            }

        } catch (InterruptedException | JMSException e) {
            logger.warning("Error while reading activeMq message");
            throw new RuntimeException(e);
        }

        closeActiveMqConnection();
        logger.info("org.example.BatterySimulator finished");
    }

    private static void handleMessage(String messageBody) {
        if ("print".startsWith(messageBody))
            logger.info("received command to print");
        else if ("charge".startsWith(messageBody)) {
            battery.charge(extractValue(messageBody));
            logger.info("battery charge = " + battery.getCurrentLoad());
        } else if ("discharge".startsWith(messageBody)) {
            battery.discharge(extractValue(messageBody));
            logger.info("battery charge = " + battery.getCurrentLoad());
        } else if ("shutdown".startsWith(messageBody))
            keepAlive = false;
        else
            logger.warning("Unknown command received = " + messageBody);
    }

    private static long extractValue(String messageBody) {
        return Long.parseLong(messageBody.split(":")[1]);
    }

    private static void establishActiveMqConnection() {
        if (activeMqSession == null) {
            logger.info("Establishing activeMq connection");

            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
            try {
                activeMqConnection = connectionFactory.createConnection();
                activeMqConnection.start();

                activeMqSession = activeMqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

                activeMqConsumer = activeMqSession.createConsumer(activeMqSession.createQueue("TESTFOO"));
            } catch (JMSException e) {
                logger.warning("Error while establishing activeMq connection");
                throw new RuntimeException(e);
            }
        }
    }

    private static void closeActiveMqConnection() {
        logger.info("Closing activeMq session and connection");
        try {
            if (activeMqSession != null)
                activeMqSession.close();
            if (activeMqConnection != null)
                activeMqConnection.close();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}
