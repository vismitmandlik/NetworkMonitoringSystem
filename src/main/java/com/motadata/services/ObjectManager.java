package com.motadata.services;

import com.motadata.Main;
import com.motadata.constants.Constants;
import com.motadata.db.Operations;
import com.motadata.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.motadata.services.Poller.poll;

public class ObjectManager extends AbstractVerticle
{
    private static long TIMER_ID = -1;

    private static final int BATCH_SIZE = 25;

    private static final Queue<JsonObject> deviceQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start()
    {
        vertx.eventBus().localConsumer(Constants.PROVISION_VERTICLE);
    }

    public void provisionDevices(RoutingContext context)
    {
        var requestBody = context.body().asJsonObject();

        if (!isValidRequest(requestBody))
        {
            context.response().setStatusCode(400).end(Utils.errorResponse("Invalid request: 'objectIds' or 'pollInterval' is missing"));

            return;
        }

        var deviceIds = requestBody.getJsonArray("objectIds");

        var pollInterval = requestBody.getInteger("pollInterval");

        var event = requestBody.getString("event");

        try
        {
            fetchDeviceDetails(deviceIds).onSuccess(devices ->
            {
                // Use splitIntoBatches to split the devices into manageable batches
//            List<JsonArray> deviceBatches = splitIntoBatches(devices);

                for (var device : devices)
                {
                    var ip = device.getString("ip");

                    var port = device.getInteger("port");

                    checkDeviceAvailability(ip, port).onSuccess(isAvailable ->
                    {
                        if (isAvailable)
                        {
                            deviceQueue.add(device);
                        }
                    }).onFailure(err -> System.err.println("Device " + ip + " not available: " + err.getMessage()));
                }

                // Start polling task for the first time, if not already started
                if (TIMER_ID == -1)
                {
                    startPollingTask( event, pollInterval);
                }

                else
                {
                    System.out.println("Polling is already active.");
                }

                context.response().setStatusCode(200).end(Utils.successResponse());

            }).onFailure(err -> context.response().setStatusCode(500).end(Utils.errorResponse(err.getMessage())));
        }

        catch (Exception exception)
        {
            System.err.println("Failed to provision device. " + exception);
        }

    }

    private boolean isValidRequest(JsonObject requestBody)
    {
        return requestBody != null && requestBody.containsKey("objectIds") && requestBody.containsKey("pollInterval");
    }

    private void startPollingTask(String event, int pollInterval)
    {
        System.out.println("Polling task started for device with interval " + pollInterval + " seconds.");

        try
        {
            TIMER_ID = Main.vertx().setPeriodic(pollInterval * 1000L, timerId ->
            {
                if (!deviceQueue.isEmpty())
                {
                    var batch = new JsonArray();

                    for (int i = 0; i < BATCH_SIZE && !deviceQueue.isEmpty(); i++)
                    {
                        batch.add(deviceQueue.poll());  // Add device to batch from the queue
                    }

                    // Use a fixed thread pool to poll devices concurrently
                    Main.vertx().eventBus().request(Constants.POLLER_VERTICLE,  new JsonObject()
                            .put("devices", batch)
                            .put("event", event), result ->
                    {
                        if (result.succeeded())
                        {
                            System.out.println("Polling initiated successfully for the batch.");
                        }

                        else
                        {
                            System.err.println("Failed to initiate polling: " + result.cause().getMessage());
                        }
                    });
                }
            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to start polling " + exception);
        }
    }

    public Future<List<JsonObject>> fetchDeviceDetails(JsonArray deviceIds)
    {
        var promise = Promise.<List<JsonObject>>promise();

        try
        {
            Operations.findAll(Constants.OBJECTS_COLLECTION, new JsonObject().put("_id", new JsonObject().put("$in", deviceIds))).onComplete(result ->
            {
                if (result.failed())
                {
                    promise.fail("No devices found with the provided IDs");
                }

                else
                {
                    promise.complete(result.result());
                }
            });
        }

        catch (Exception exception)
        {
            promise.fail("Exception in fetching device details. " + exception.getMessage());
        }

        return promise.future();
    }

    static void storePollerResults(JsonArray pollerResults)
    {
        var timestamp = System.currentTimeMillis();

        try
        {
            pollerResults.forEach(resultObj ->
            {
                var result = Utils.parsePollerResult((String) resultObj);

                if (result != null)
                {
                    result.put("timestamp", timestamp);

                    Operations.insert(Constants.POLLER_RESULTS_COLLECTION, result).onComplete(asyncResult ->
                    {
                        if(asyncResult.succeeded())
                        {
                            System.out.println("Poller result stored in database: " + asyncResult.result());

                        }

                        else
                        {
                            System.err.println("Failed to store result " + asyncResult.cause());
                        }
                    });
                }
            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to store poller result. " + exception);
        }

    }

    // Checks if the device IP is reachable and if the port is open
    private Future<Boolean> checkDeviceAvailability(String ip, int port)
    {
        try
        {
            return Utils.ping(ip).compose(isReachable ->
            {
                if (isReachable)
                {
                    return Utils.checkPort(ip, port);
                }

                else
                {
                    return Future.failedFuture("Device is not reachable");
                }
            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to check device availability. " + exception);

            return Future.failedFuture(exception);
        }

    }

}
