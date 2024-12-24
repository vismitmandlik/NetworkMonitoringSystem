package com.motadata.services;

import com.motadata.Main;
import com.motadata.constants.Constants;
import com.motadata.db.Operations;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class ObjectManager
{
    private static long TIMER_ID = -1;
    
    public static void provisionDevices(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        if (!isValidRequest(requestBody))
        {
            context.response().setStatusCode(400).end(errorResponse("Invalid request: 'objectIds' or 'pollInterval' is missing"));

            return;
        }

        var deviceIds = requestBody.getJsonArray("objectIds");

        var pollInterval = requestBody.getInteger("pollInterval");

        var event = requestBody.getString("event");

        fetchDeviceDetails(deviceIds).onSuccess(devices ->
        {
            // Start polling task for the first time, if not already started
            if (TIMER_ID == -1)
            {
                startPollingTask(devices, event, pollInterval);
            }
            else
            {
                System.out.println("Polling is already active.");
            }

            context.response().setStatusCode(200).end(successResponse());

        }).onFailure(err -> context.response().setStatusCode(500).end(errorResponse(err.getMessage())));
    }

    private static boolean isValidRequest(JsonObject requestBody)
    {
        return requestBody != null && requestBody.containsKey("objectIds") && requestBody.containsKey("pollInterval");
    }

    private static void startPollingTask(List<JsonObject> devices, String event, int pollInterval)
    {
        System.out.println("Polling task started for device with interval " + pollInterval + " seconds.");

        TIMER_ID = Main.vertx().setPeriodic(pollInterval * 1000L, timerId ->
        {
            for (  JsonObject device : devices)
            {
                pollDevice(device, event);
            }
        });
    }

    private static void pollDevice(JsonObject device, String event)
    {
        Main.vertx().executeBlocking(promise ->
        {
            Process process = null;

            try
            {
                var devicesJsonString = new JsonArray().add(device).encode();

                var goExecutable = Main.vertx().getOrCreateContext().config().getString("goExecutablePath");

                process = new ProcessBuilder(goExecutable, event, devicesJsonString).start();

                var outputLines = new JsonArray();

                // Use try-with-resources to handle BufferedReader
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
                {
                    String line;

                    while ((line = reader.readLine()) != null)
                    {
                        outputLines.add(line);
                    }
                }

                process.waitFor();

                // Print polling result
                if (process.exitValue() == 0)
                {
                    System.out.println("Polling result for device " + device.getString("_id") + ": " + outputLines.encodePrettily());

                    storePollerResults(outputLines);
                }
                else
                {
                    System.out.println("Polling failed for device " + device.getString("_id"));
                }
            }

            catch (Exception exception)
            {
                exception.printStackTrace();
            }

            finally
            {
                // Ensure the process is destroyed if it's still running
                if (process != null && process.isAlive())
                {
                    process.destroy();

                    System.out.println("Polling process for device " + device.getString("_id") + " was destroyed.");
                }

                promise.complete("Poll completed");
            }
        }, asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                System.out.println(asyncResult.result());
            }
        });
    }

    public static Future<List<JsonObject>> fetchDeviceDetails(JsonArray deviceIds)
    {
        var promise = Promise.<List<JsonObject>>promise();

        Operations.findAll(Constants.OBJECTS_COLLECTION, new JsonObject().put("_id", new JsonObject().put("$in", deviceIds))).onSuccess(devices ->
        {
            if (devices.isEmpty())
            {
                promise.fail("No devices found with the provided IDs");
            }
            else
            {
                promise.complete(devices);
            }
        }).onFailure(promise::fail);

        return promise.future();
    }

    private static void storePollerResults(JsonArray pollerResults)
    {
        var timestamp = System.currentTimeMillis();

        pollerResults.forEach(resultObj ->
        {
            var result = parsePollerResult((String) resultObj);

            if (result != null)
            {
                result.put("timestamp", timestamp);

                Operations.insert(Constants.POLLER_RESULTS_COLLECTION, result).onSuccess(res -> System.out.println("Poller result stored in database: " + result.encodePrettily())).onFailure(err -> System.err.println("Insert failed: " + err.getMessage()));
            }
        });
    }

    private static JsonObject parsePollerResult(String pollerResult)
    {
        try
        {
            var result = new JsonObject(pollerResult);

            var deviceId = result.getString("deviceId");

            if (deviceId == null || deviceId.isEmpty())

            {
                System.err.println("DeviceId is null or empty in result: " + result);

                return null;
            }

            return new JsonObject()
                    .put("deviceId", deviceId)
                    .put("ip", result.getString("ip"))
                    .put("cpuUsage", parseDouble(result.getString("cpuUsage")))
                    .put("memoryUsage", parseDouble(result.getString("memoryUsage")))
                    .put("diskUsage", parseDouble(result.getString("diskUsage").replace("%", "")));
        }
        catch (Exception exception)
        {
            System.err.println("Failed to parse poller result: " + pollerResult + exception);

            return null;
        }
    }

    private static double parseDouble(String value)
    {
        try
        {
            return Double.parseDouble(value);
        }
        catch (NumberFormatException exception)
        {
            return 0.0;
        }
    }

    private static String errorResponse(String message)
    {
        return new JsonObject().put("error", message).toString();
    }

    private static String successResponse()
    {
        return new JsonObject().put("message", "Polling tasks started for provisioned devices").toString();
    }
}
