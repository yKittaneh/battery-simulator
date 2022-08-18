package org.example.separate;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.example.Battery;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.lang.Math.abs;

public class BatterySimulator extends Simulator {

    // todo: does the battery need to be a simulator plugged in to mosaik?
    // todo: if no, then it should run on the same docker as the edge node
    // todo: if yes, does the battery connect to the grid? Or to the node? Or to both? What kind of info does it exchange

    private static final Logger logger = Logger.getLogger(BatterySimulator.class.getName());

    private static final JSONObject META = (JSONObject) JSONValue.parse(("{"
            + "    'api_version': " + Simulator.API_VERSION + ","
            + "    'models': {"
            + "        'Battery': {"
            + "            'public': true,"
            + "            'params': ['grid_node_id'],"
            + "            'attrs': ['grid_node_id', 'current_load', 'grid_power', 'charge', 'discharge']"
            + "        }"
            + "    }"
            + "}").replace("'", "\""));

    public static String GRID_NAME;

    private static String ENTITY_ID;

    private static String GRID_NODE_ID;

    private static Long PROFILE_RESOLUTION; //aka step size

    private static Battery battery;

    private static Connection activeMqConnection;

    private static Session activeMqSession;

    private static MessageConsumer activeMqConsumer;

    private static boolean keepAlive = true;

    public BatterySimulator(String simName) {
        super(simName);
    }

    public static void main(String[] strings) throws Exception {
        logger.info("org.example.BatterySimulator started...");

//        battery = new Battery(100L, 0);

//        establishActiveMqConnection();

//        try {
//            while (keepAlive) {
//                Thread.sleep(500);
//
//                TextMessage message = (TextMessage) activeMqConsumer.receive();
//
//                if (message == null)
//                    continue;
//
//                String messageBody = message.getText();
//
//                logger.info("messageBody = " + messageBody);
//
//                handleMessage(messageBody);
//            }
//
//        } catch (InterruptedException | JMSException e) {
//            logger.warning("Error while reading activeMq message");
//            throw new RuntimeException(e);
//        }
//
//        closeActiveMqConnection();


        Simulator sim = new BatterySimulator("simName");
        if (strings.length < 1) {
            String[] ipaddr = {"127.0.0.1:8000"};
            SimProcess.startSimulation(ipaddr, sim);
        } else {
            logger.info("args: " + Arrays.toString(strings));
            SimProcess.startSimulation(strings, sim);
        }

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

    @Override
    public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
        logger.info("init called ");

        if (simParams.containsKey("eid"))
            ENTITY_ID = simParams.get("eid").toString();

//        if (simParams.containsKey("grid_name"))
//            GRID_NAME = simParams.get("grid_name").toString();

        if (simParams.containsKey("profile_resolution"))
            PROFILE_RESOLUTION = (Long) simParams.get("profile_resolution");

        // todo: needs to connect to queue? same queue (in a different docker)?
//        postMessageToQueue("BatterySimulator initiated");

        return META;
    }

    @Override
    public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams) {
        logger.info("create called ");
        if (num != 1)
            throw new RuntimeException("Value of param 'num' in create method = [" + num + "], expected 1. System design only allows for one entity per simulation.");

        if (!modelParams.containsKey("grid_node_id"))
            throw new RuntimeException("could not find param grid_node_id while creating battery entity");

        GRID_NODE_ID = (String) modelParams.get("grid_node_id");

        logger.info("connecting to grid node [gridNodeId = " + GRID_NODE_ID + "].");

        battery = new Battery(50000, 0);

        JSONArray entities = new JSONArray();

        JSONObject entity = new JSONObject();
        entity.put("eid", ENTITY_ID);
        entity.put("type", model);
        entity.put("rel", new JSONArray());
        entity.put("node_id", GRID_NODE_ID);

        entities.add(entity);
        return entities;
    }

    @Override
    public long step(long time, Map<String, Object> inputs, long maxAdvance) {
        logger.info("step called ");

        long minutes = time / 60;

        // todo (medium): for loop below is not needed? extract gridPower value directly? -- rn it is needed bcs we have another attr (pvPower)
        for (Map.Entry<String, Object> entity : inputs.entrySet()) {
            Map<String, Object> attributes = (Map<String, Object>) entity.getValue();
            for (Map.Entry<String, Object> attr : attributes.entrySet()) {
                if (attr.getKey().equals("charge"))
                    chargeBattery(((Number) ((JSONObject) attr.getValue()).values().toArray()[0]).floatValue());
                if (attr.getKey().equals("discharge"))
                    dischargeBattery(((Number) ((JSONObject) attr.getValue()).values().toArray()[0]).floatValue());
            }
        }

        return (minutes + PROFILE_RESOLUTION) * 60;
    }


    @Override
    public Map<String, Object> getData(Map<String, List<String>> outputs) {
        logger.info("getData called ");

        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<String, List<String>> entity : outputs.entrySet()) {
            String eid = entity.getKey();
            List<String> attrs = entity.getValue();
            HashMap<String, Object> values = new HashMap<>();
            for (String attr : attrs) {
                switch (attr) {
                    case "grid_node_id":
                        values.put(attr, GRID_NODE_ID);
                        break;
                    case "current_load":
                        values.put(attr, battery.getCurrentLoad());
                        break;
                }
            }
            data.put(eid, values);
        }
        return data;
    }

    private void chargeBattery(float chargeValue) {
        logger.info("+++ chargeValue = [" + chargeValue + "]");
        battery.charge(abs(chargeValue));
        logger.info("battery currentLoad = " + battery.getCurrentLoad());
    }

    private void dischargeBattery(float dischargeValue) {
        logger.info("+++ dischargeValue = [" + dischargeValue + "]");
        battery.discharge(abs(dischargeValue));
        logger.info("battery currentLoad = " + battery.getCurrentLoad());
    }
}