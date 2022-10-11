package org.example;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.lang.Math.abs;

public class BatterySimulator extends Simulator {

    // todo: does the battery need to be a simulator plugged in to mosaik? yes
    // todo: if yes, does the battery connect to the grid? Or to the edge node? Or to both? What kind of info does it exchange? --> ... it connects to a new control node

    private static final Logger logger = Logger.getLogger(BatterySimulator.class.getName());

    private static final JSONObject META = (JSONObject) JSONValue.parse(("{"
            + "    'api_version': " + Simulator.API_VERSION + ","
            + "    'type': 'time-based',"
            + "    'models': {"
            + "        'Battery': {"
            + "            'public': true,"
            + "            'params': ['grid_node_id'],"
            + "            'attrs': ['grid_node_id', 'current_load', 'grid_power', 'charge', 'discharge', 'test', 'battery_action']"
            + "            'non-persistent': ['test']"
            + "        }"
            + "    }"
            + "}").replace("'", "\""));

    private static String ENTITY_ID;

    private static String GRID_NODE_ID;

    private static Long PROFILE_RESOLUTION; //aka step size

    private static Battery battery;

    private static boolean keepAlive = true;

    /*
    # defaults for lithium NMC cell, including DC-AC inverter loss
    charging_efficiency = 0.98
    discharging_efficiency = 1.05


    #             c_lim = 3, d_lim = 3,
    #             upper_u = -0.125, upper_v = 1,
    #             lower_u = 0.05, lower_v = 0
     */

    public BatterySimulator(String simName) {
        super(simName);
    }

    public static void main(String[] strings) throws Exception {
        logger.info("org.example.BatterySimulator started...");

        runAsMosaikSimulation(strings);

        logger.info("org.example.BatterySimulator finished");
    }

    private static void runAsMosaikSimulation(String[] strings) throws Exception {
        Simulator sim = new BatterySimulator("simName");
        if (strings.length < 1) {
            String[] ipaddr = {"127.0.0.1:8000"};
            SimProcess.startSimulation(ipaddr, sim);
        } else {
            logger.info("args: " + Arrays.toString(strings));
            SimProcess.startSimulation(strings, sim);
        }
    }

//    private static void handleMessage(String messageBody) {
//        if (messageBody.startsWith("print"))
//            logger.info("received command to print");
//        else if (messageBody.startsWith("charge")) {
//            battery.charge(extractValue(messageBody));
//            logger.info("battery charge = " + battery.getCurrentLoad());
//        } else if (messageBody.startsWith("discharge")) {
//            battery.discharge(extractValue(messageBody));
//            logger.info("battery charge = " + battery.getCurrentLoad());
//        } else if (messageBody.startsWith("shutdown"))
//            keepAlive = false;
//        else
//            logger.warning("Unknown command received = " + messageBody);
//    }

//    private static long extractValue(String messageBody) {
//        return Long.parseLong(messageBody.split(":")[1]);
//    }

    @Override
    public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
        logger.info("init called ");

        if (simParams.containsKey("eid"))
            ENTITY_ID = simParams.get("eid").toString();

        if (simParams.containsKey("profile_resolution"))
            PROFILE_RESOLUTION = (Long) simParams.get("profile_resolution");

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

        String message;
        String action;
        String amount;

        // todo (medium): for loop below is not needed? extract gridPower value directly? -- rn it is needed bcs we have another attr (pvPower)
        for (Map.Entry<String, Object> entity : inputs.entrySet()) {
            Map<String, Object> attributes = (Map<String, Object>) entity.getValue();
            for (Map.Entry<String, Object> attr : attributes.entrySet()) {
                if (attr.getKey().equals("charge"))
                    chargeBattery(((Number) ((JSONObject) attr.getValue()).values().toArray()[0]).floatValue());
                if (attr.getKey().equals("discharge"))
                    dischargeBattery(((Number) ((JSONObject) attr.getValue()).values().toArray()[0]).floatValue());
                if (attr.getKey().equals("test"))
                    logger.info("received test value [" + ((Number) ((JSONObject) attr.getValue()).values().toArray()[0]).floatValue() + "]");
                if (attr.getKey().equals("battery_action")) {
                    logger.info("received battery_action message: " + attr.getValue());

                    message = (String) ((JSONObject) attr.getValue()).values().toArray()[0];
                    if (message == null)
                        continue;
                    action = message.split(":")[0];
                    amount = message.split(":")[1];

                    if ("charge".equals(action))
                        chargeBattery(Float.parseFloat(amount));
                    else if ("discharge".equals(action))
                        dischargeBattery(Float.parseFloat(amount));
                    else {
                        logger.warning("Unknown action received [" + action + "]");
                        throw new RuntimeException("Unknown action received [" + action + "]");
                    }
                }
            }
        }

        return (minutes + PROFILE_RESOLUTION) * 60;
//        return null;
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
                    default:
                        logger.warning("unexpected attr requested [" + attr + "]");
                        throw new RuntimeException("unexpected attr requested [" + attr + "]");
                }
            }
            data.put(eid, values);
        }
        return data;
    }

    private void chargeBattery(float chargeValue) {
        logger.info("+++ chargeValue = [" + chargeValue + "]");
        if (chargeValue == 0)
            return;
        battery.charge(abs(chargeValue));
        logger.info("battery currentLoad = " + battery.getCurrentLoad());
    }

    private void dischargeBattery(float dischargeValue) {
        logger.info("+++ dischargeValue = [" + dischargeValue + "]");
        if (dischargeValue == 0)
            return;
        battery.discharge(abs(dischargeValue));
        logger.info("battery currentLoad = " + battery.getCurrentLoad());
    }
}
