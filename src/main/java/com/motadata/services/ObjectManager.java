package com.motadata.services;

import com.motadata.Main;
import com.motadata.db.Operations;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ObjectManager {

    static Vertx vertx = Main.getVertxInstance();

    public static Future<JsonArray> pollDevices(RoutingContext context)
    {

        var promise = Promise.<JsonArray>promise();

        // Extract request body as JSON
        var requestBody = context.body().asJsonObject();

        if (requestBody == null || !requestBody.containsKey("objectIds"))
        {
            promise.fail("Invalid request body: 'devices' key missing");

            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject().put("error ","Invalid request body: 'devices' key missing").toBuffer());

            return promise.future();
        }

        var deviceIds = requestBody.getJsonArray("objectIds");

        // Fetch device details based on the provided device IDs
        fetchDeviceDetails(deviceIds).onSuccess(devices ->
        {
            vertx.executeBlocking(blockingPromise ->
            {
                try
                {
                    // Convert the list of devices to a JsonArray
                    var devicesJsonArray = new JsonArray(devices);

                    // Encode the JsonArray to a JSON string
                    var devicesJsonString = devicesJsonArray.encode(); // Convert to string

                    System.out.println("Devices JSON String: " + devicesJsonString);

                    // Path to your Go executable
                    var goExecutable = "/home/vismit/vismit/learning/new/Golang/GoSpawn/cmd/poller";

                    // Log the absolute path
//                    System.out.println("Absolute Path: " + new java.io.File(goExecutable).getAbsolutePath());

                    // Build process
                    var processBuilder = new ProcessBuilder(goExecutable, devicesJsonString);

                    System.out.println("Executing: " + processBuilder.command());

                    processBuilder.redirectErrorStream(true);

                    System.out.println("Spawned go");
                    // Start process
                    var process = processBuilder.start();

                    // Capture output
                    var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    var outputLines = new ArrayList<String>();

                    var line = "";

                    while ((line = reader.readLine()) != null)
                    {
                        outputLines.add(line);

                        System.out.println("Go Output: " + line);
                    }

                    process.waitFor();

                    if (process.exitValue() == 0)
                    {
                        // Successful output
                        var outputJson = new JsonArray(outputLines);

                        System.out.println("Go output" + outputJson);

                        storePollerResults(outputJson);

                        blockingPromise.complete(outputJson);
                    }
                    else
                    {
                        blockingPromise.fail("Poller exited with non-zero status code");
                    }
                }
                catch (Exception e)
                {
                    blockingPromise.fail(e);
                }
            }, result ->
            {
                if (result.succeeded())
                {
//                    promise.complete((JsonArray) result.result());
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .setStatusCode(200)
                            .end(((JsonArray) result.result()).encode());

                }
                else
                {
//                    promise.fail(result.cause());
                    context.response()
                            .setStatusCode(500)
                            .end(new JsonObject().put("error", result.cause().getMessage()).encode());
                }
            });
        }).onFailure(err ->
        {
            // Handle fetchDeviceDetails failure
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject().put("error", err.getMessage()).encode());
        });

        return promise.future();
    }

    public static Future<List<JsonObject>> fetchDeviceDetails(JsonArray deviceIds)
    {

        var promise = Promise.<List<JsonObject>>promise();

        // Query the database to get device details for the provided device IDs
        Operations.findAll("objects", new JsonObject().put("_id", new JsonObject().put("$in", deviceIds)))
                .onSuccess(devices ->
                {
                    if (devices.isEmpty())
                    {
                        System.out.println("No devices found with the provided IDs");
                        promise.fail("No devices found with the provided IDs");
                    }
                    else
                    {
                        System.out.println("Devices fetched");
                        promise.complete(devices);
                    }
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    private static void storePollerResults(JsonArray pollerResults)
    {
        vertx.executeBlocking(promise ->
        {
            try {
                var timestamp = System.currentTimeMillis();  // Current timestamp

                for (int i = 0; i < pollerResults.size(); i++)
                {
                    try
                    {
                        // Parse each line as a JSON object directly
                        JsonObject result = new JsonObject(pollerResults.getString(i));

                        // Extract required fields
                        String deviceId = result.getString("deviceId");
                        String Ip = result.getString("ip");
                        double cpuUsage = parseUsage(result.getString("cpuUsage"));
                        double memoryUsage = parseUsage(result.getString("memoryUsage"));
                        double diskUsage = parseDiskUsage(result.getString("diskUsage"));

                        // Check for missing deviceId
                        if (deviceId == null || deviceId.isEmpty()) {
                            System.err.println("DeviceId is null or empty in result: " + result);
                            continue;  // Skip this entry
                        }

                        // Create a data object to store
                        JsonObject dataToStore = new JsonObject()
                                .put("deviceId", deviceId)
                                .put("ip", Ip)
                                .put("cpuUsage", cpuUsage)
                                .put("memoryUsage", memoryUsage)
                                .put("diskUsage", diskUsage)
                                .put("timestamp", timestamp);

                        // Print for debugging
                        System.out.println("Data to store: " + dataToStore);

                        // Insert into MongoDB
                        Operations.insert("poller_results", dataToStore)
                                .onFailure(err -> System.err.println("Insert failed: " + err.getMessage()));

                    } catch (Exception e) {
                        System.err.println("Error processing result: " + e.getMessage());
                    }
                }

                promise.complete(); // Task completed successfully
            }
            catch (Exception e)
            {
                promise.fail(e); // Handle any top-level exception
            }
        }).onSuccess(v ->
        {
            System.out.println("All poller results stored successfully.");
        }).onFailure(err ->
        {
            System.err.println("Error storing poller results: " + err.getMessage());
        });
    }

    // Helper method to parse CPU/Memory usage strings (like "12.40")
    private static double parseUsage(String usage)
    {
        try
        {
            return Double.parseDouble(usage);
        }
        catch (NumberFormatException e)
        {
            return 0.0;  // Return 0.0 if parsing fails
        }
    }

    // Helper method to parse disk usage percentages (like "5%")
    private static double parseDiskUsage(String diskUsage)
    {
        try
        {
            return Double.parseDouble(diskUsage.replace("%", ""));
        }
        catch (NumberFormatException e)
        {
            return 0.0;  // Return 0.0 if parsing fails
        }
    }
}
