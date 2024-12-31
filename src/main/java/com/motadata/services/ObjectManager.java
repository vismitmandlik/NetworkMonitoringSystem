package com.motadata.services;

import com.motadata.Main;
import com.motadata.constants.Constants;
import com.motadata.db.Operations;
import com.motadata.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ObjectManager extends AbstractVerticle
{
    private static long TIMER_ID = -1;

    private static final int BATCH_SIZE = 25;

    public static final String OBJECT_IDS = "objectIds";

    public static final String POLL_INTERVAL = "pollInterval";

    public static final String EVENT = "event";

    public static final String DEVICES = "devices";

    public static final String TIMESTAMP = "timestamp";

    private static final Queue<JsonObject> deviceQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start(Promise<Void> promise)
    {
        vertx.eventBus().localConsumer(Constants.PROVISION_VERTICLE, this::provision);

        promise.complete();
    }

    public void provision(Message<Object> message)
    {
        var requestBody = (JsonObject) message.body();

        if (!Utils.isValidRequest(requestBody))
        {
            message.fail(Constants.SC_400, "Invalid request: 'objectIds' or 'pollInterval' is missing");

            return;
        }

        var deviceIds = requestBody.getJsonArray(OBJECT_IDS);

        var pollInterval = requestBody.getInteger(POLL_INTERVAL);

        var event = requestBody.getString(EVENT);

        try
        {
            fetch(deviceIds).onComplete(result ->
            {
                if(result.succeeded())
                {
                    for (var item : result.result())
                    {
                        var ip = item.getString(Constants.IP);

                        var port = item.getInteger(Constants.PORT);

                        checkDeviceAvailability(ip, port).onComplete(asyncResult ->
                        {
                            if (asyncResult.succeeded())
                            {
                                deviceQueue.add(item);
                            }
                            else
                            {
                                System.err.println("Device " + ip + " not available: " + asyncResult.cause());
                            }
                        });
                    }

                    // Start polling task for the first time, if not already started
                    if (TIMER_ID == -1)
                    {
                        send(event, pollInterval);
                    }
                    else
                    {
                        System.out.println("Polling is already active.");
                    }

                    message.reply(Utils.successResponse());
                }
                else
                {
                    message.fail(Constants.SC_500, result.cause().toString());
                }

            });
        }

        catch (Exception exception)
        {
            message.fail(Constants.SC_500, "Failed to provision device: " + exception.getMessage());
        }
    }

    /* It sends devices in batches to poller verticle */
    private void send(String event, int pollInterval)
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
                            .put(DEVICES, batch)
                            .put(EVENT, event), result ->
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

    /* It fetches device details using deviceId from the database */
    public Future<List<JsonObject>> fetch(JsonArray deviceIds)
    {
        var promise = Promise.<List<JsonObject>>promise();

        try
        {
            Operations.findAll(Constants.OBJECTS_COLLECTION, new JsonObject().put(Constants.ID, new JsonObject().put("$in", deviceIds))).onComplete(result ->
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

    /* It stores poller result into database */
    static void store(JsonArray pollerResults)
    {
        var timestamp = System.currentTimeMillis();

        try
        {
            pollerResults.forEach(resultObj ->
            {
                var result = Utils.parsePollerResult((String) resultObj);

                if (result != null)
                {
                    result.put(TIMESTAMP, timestamp);

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

    /* Checks if the device IP is reachable and if the port is open */
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
