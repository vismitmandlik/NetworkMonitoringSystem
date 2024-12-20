//package com.motadata.services;
//
//import com.motadata.Main;
//import com.motadata.db.Operations;
//import io.vertx.core.Future;
//import io.vertx.core.Promise;
//import io.vertx.core.Vertx;
//import io.vertx.core.json.JsonArray;
//import io.vertx.core.json.JsonObject;
//import io.vertx.ext.web.RoutingContext;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class tempObjectManager {
//
//    static final Vertx vertx = Main.getVertxInstance();
//
//    private static final Map<String, Long> pollingTasks = new HashMap<>();
//
//    public static void provisionDevices(RoutingContext context)
//    {
//        var requestBody = context.body().asJsonObject();
//
//        if (requestBody == null || !requestBody.containsKey("objectIds") || !requestBody.containsKey("pollInterval"))
//        {
//            context.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid request: 'objectIds' or 'pollInterval' is missing").toBuffer());
//
//            return;
//        }
//
//        var deviceIds = requestBody.getJsonArray("objectIds");
//
//        var pollInterval = requestBody.getInteger("pollInterval");
//
//        var event = requestBody.getString("event");
//
//        fetchDeviceDetails(deviceIds).onSuccess(devices ->
//        {
//            for (JsonObject device : devices)
//            {
//                startPollingTask(device, event, pollInterval);
//            }
//
//            context.response().setStatusCode(200).end(new JsonObject().put("message", "Polling tasks started for provisioned devices").toBuffer());
//        }).onFailure(err ->
//        {
//            context.response().setStatusCode(500).end(new JsonObject().put("error", err.getMessage()).toBuffer());
//        });
//    }
//
//    private static void startPollingTask(JsonObject device, String event,  int pollInterval)
//    {
//        String deviceId = device.getString("_id");
//
//        if (pollingTasks.containsKey(deviceId))
//        {
//            vertx.cancelTimer(pollingTasks.get(deviceId));
//        }
//
//        long timerId = vertx.setPeriodic(pollInterval * 1000L, id -> pollDevice(device, event));
//
//        pollingTasks.put(deviceId, timerId);
//
//        System.out.println("Polling task started for device " + deviceId + " with interval " + pollInterval + " seconds.");
//    }
//
//    private static void pollDevice(JsonObject device, String event)
//    {
//        vertx.executeBlocking(blockingPromise ->
//        {
//            try
//            {
//                var devicesJsonString = new JsonArray().add(device).encode();
//
//                var goExecutable = "/home/vismit/vismit/learning/new/Golang/GoSpawn/cmd/main";
//
//                var processBuilder = new ProcessBuilder(goExecutable, event, devicesJsonString);
//
//                System.out.println(goExecutable + " " + event + " " + devicesJsonString);
//
//                var process = processBuilder.start();
//
//                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//
//                var outputLines = new JsonArray();
//
//                String line;
//
//                while ((line = reader.readLine()) != null)
//                {
//                    outputLines.add(line);
//                }
//
//                process.waitFor();
//
//                if (process.exitValue() == 0)
//                {
//                    storePollerResults(outputLines);
//
//                    blockingPromise.complete();
//                }
//                else
//                {
//                    blockingPromise.fail("Go process failed with non-zero exit code.");
//                }
//            }
//
//            catch (Exception e)
//            {
//                blockingPromise.fail(e);
//            }
//
//        }).onFailure(err ->
//        {
//            System.err.println("Polling failed for device " + device.getString("_id") + ": " + err.getMessage());
//        });
//    }
//
//    public static Future<List<JsonObject>> fetchDeviceDetails(JsonArray deviceIds)
//    {
//        var promise = Promise.<List<JsonObject>>promise();
//
//        Operations.findAll("objects", new JsonObject().put("_id", new JsonObject().put("$in", deviceIds)))
//                .onSuccess(devices ->
//                {
//                    if (devices.isEmpty())
//                    {
//                        promise.fail("No devices found with the provided IDs");
//                    }
//                    else
//                    {
//                        promise.complete(devices);
//                    }
//                })
//                .onFailure(promise::fail);
//
//        return promise.future();
//    }
//
//    private static void storePollerResults(JsonArray pollerResults)
//    {
//        vertx.executeBlocking(promise ->
//        {
//            try
//            {
//                var timestamp = System.currentTimeMillis();
//
//                for (int i = 0; i < pollerResults.size(); i++)
//                {
//                    JsonObject result = new JsonObject(pollerResults.getString(i));
//
//                    String deviceId = result.getString("deviceId");
//
//                    String Ip = result.getString("ip");
//
//                    double cpuUsage = parseUsage(result.getString("cpuUsage"));
//
//                    double memoryUsage = parseUsage(result.getString("memoryUsage"));
//
//                    double diskUsage = parseDiskUsage(result.getString("diskUsage"));
//
//                    // Check for missing deviceId
//                    if (deviceId == null || deviceId.isEmpty())
//                    {
//                        System.err.println("DeviceId is null or empty in result: " + result);
//
//                        continue;  // Skip this entry
//                    }
//
//                    // Create a data object to store
//                    JsonObject dataToStore = new JsonObject()
//                            .put("deviceId", deviceId)
//                            .put("ip", Ip)
//                            .put("cpuUsage", cpuUsage)
//                            .put("memoryUsage", memoryUsage)
//                            .put("diskUsage", diskUsage)
//                            .put("timestamp", timestamp);
//
//                    System.out.println(dataToStore);
//
//                    Operations.insert("poller_results", dataToStore).onFailure(err -> System.err.println("Insert failed: " + err.getMessage()));
//                }
//
//                promise.complete();
//            }
//            catch (Exception e)
//            {
//                promise.fail(e);
//            }
//        }).onFailure(err ->
//        {
//            System.err.println("Error storing poller results: " + err.getMessage());
//        });
//    }
//
//    private static double parseUsage(String usage)
//    {
//        try
//        {
//            return Double.parseDouble(usage);
//        }
//        catch (NumberFormatException e)
//        {
//            return 0.0;  // Return 0.0 if parsing fails
//        }
//    }
//
//    private static double parseDiskUsage(String diskUsage)
//    {
//        try
//        {
//            return Double.parseDouble(diskUsage.replace("%", ""));
//        }
//        catch (NumberFormatException e)
//        {
//            return 0.0;  // Return 0.0 if parsing fails
//        }
//    }
//}
