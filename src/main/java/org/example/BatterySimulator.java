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

    private static final Logger logger = Logger.getLogger(BatterySimulator.class.getName());

    private static final JSONObject META = (JSONObject) JSONValue.parse(("{"
            + "    'api_version': " + Simulator.API_VERSION + ","
            + "    'type': 'time-based',"
            + "    'models': {"
            + "        'Battery': {"
            + "            'public': true,"
            + "            'params': ['grid_node_id', 'max_capacity'],"
            + "            'attrs': ['current_load', 'battery_action']"
            + "        }"
            + "    }"
            + "}").replace("'", "\""));

    private static String ENTITY_ID;

    private static Float TIME_RESOLUTION;

    private static long STEP_SIZE;

    private static Battery battery;

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

    @Override
    public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
        logger.info("init called ");

        ENTITY_ID = "battery";

        if (timeResolution != null)
            TIME_RESOLUTION = timeResolution;

        if (simParams.containsKey("step_size"))
            STEP_SIZE = (Long) simParams.get("step_size");

        return META;
    }

    @Override
    public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams) {
        logger.info("create called ");
        if (num != 1)
            throw new RuntimeException("Value of param 'num' in create method = [" + num + "], expected 1. System design only allows for one entity per simulation.");

        createBattery(modelParams);

        JSONArray entities = new JSONArray();

        JSONObject entity = new JSONObject();
        entity.put("eid", ENTITY_ID);
        entity.put("type", model);
        entity.put("rel", new JSONArray());

        entities.add(entity);
        return entities;
    }

    @Override
    public long step(long time, Map<String, Object> inputs, long maxAdvance) {
        logger.info("step called ");

        Map.Entry<String, Object> entity = (HashMap.Entry<String, Object>) inputs.entrySet().toArray()[0];
        Map<String, Object> attributes = (Map<String, Object>) entity.getValue();
        Map.Entry<String, Object> attr = (Map.Entry<String, Object>) attributes.entrySet().toArray()[0];

        if (!attr.getKey().equals("battery_action"))
            throw new RuntimeException("did not find battery_action attribute in input map");

        logger.info("received battery_action message: " + attr.getValue());

        String message = (String) ((JSONObject) attr.getValue()).values().toArray()[0];
        handleBatteryAction(message);

        return time + STEP_SIZE;
    }

    @Override
    public Map<String, Object> getData(Map<String, List<String>> outputs) {
        logger.info("getData called ");

        Map<String, Object> data = new HashMap<>();

        Map.Entry<String, List<String>> entity = (Map.Entry<String, List<String>>) outputs.entrySet().toArray()[0];
        HashMap<String, Object> values = new HashMap<>();
        String attr = entity.getValue().get(0);

        if ("current_load".equals(attr)) {
            values.put(attr, battery.getCurrentLoad());
        } else {
            logger.warning("unexpected attr requested [" + attr + "]");
            throw new RuntimeException("unexpected attr requested [" + attr + "]");
        }
        data.put(entity.getKey(), values);

        return data;
    }

    private static void createBattery(Map<String, Object> modelParams) {
        if (!modelParams.containsKey("max_capacity")) {
            logger.warning("max_capacity not provided, using default = " + 50000);
            battery = new Battery(50000, 0);
        } else {
            logger.info("creating battery with max capacity = " + modelParams.get("max_capacity"));
            battery = new Battery(objectToFloat(modelParams.get("max_capacity")), 0);
        }
    }

    private void handleBatteryAction(String message) {
        if (message != null) {
            String action = message.split(":")[0];
            String amount = message.split(":")[1];

            if ("charge".equals(action))
                chargeBattery(objectToFloat(amount));
            else if ("discharge".equals(action))
                dischargeBattery(objectToFloat(amount));
            else if ("noAction".equals(action))
                logger.info("noAction command received!");
            else {
                logger.warning("Unknown action received [" + action + "]");
                throw new RuntimeException("Unknown action received [" + action + "]");
            }
        }
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

    public static Float objectToFloat(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Float) {
            return (Float) o;
        }
        try {
            return Float.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
