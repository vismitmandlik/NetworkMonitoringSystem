package com.motadata.services;

import com.motadata.Main;
import com.motadata.constants.Constants;
import com.motadata.db.Operations;
import com.motadata.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ObjectManager extends AbstractVerticle
{
    private static final int BATCH_SIZE = 25;

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectManager.class);

    private static final Queue<JsonObject> deviceQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start(Promise<Void> promise)
    {
        // EventBus consumer to handle provisioning
        vertx.eventBus().localConsumer(Constants.PROVISION, this::provision);

        // EventBus consumer to handle polling results request
        vertx.eventBus().consumer(Constants.OBJECT_POLLING_DATA, this::getPollerResult);

        // EventBus consumer for object deletion
        vertx.eventBus().consumer(Constants.OBJECT_DELETE, this::deleteObject);

        promise.complete();
    }

    public void provision(Message<JsonObject> message)
    {
        var requestBody = message.body();

        if (!Utils.isValidRequest(requestBody))
        {
            message.fail(Constants.SC_400, "Invalid request: 'objectIds' or 'pollInterval' is missing");

            return;
        }

        var objectIds = requestBody.getJsonArray(Constants.OBJECT_IDS);

        var pollInterval = requestBody.getInteger(Constants.POLL_INTERVAL);

        var event = requestBody.getString(Constants.EVENT);

        try
        {
            fetch(objectIds).onComplete(result ->
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
                                // Add the device with the current timestamp to the queue
                                item.put(Constants.LAST_POLL_TIME, System.currentTimeMillis());

                                deviceQueue.add(item);
                            }
                            else
                            {
                                LOGGER.error("Device {} not available: {}", ip, asyncResult.cause().getMessage());
                            }
                        });
                    }

                    send(event, pollInterval);

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
        LOGGER.info("Polling started");

        try
        {
            // Run every minute
            Main.vertx().setPeriodic(60 * 1000L, timerId ->
            {
                var currentTime = System.currentTimeMillis();

                var batch = new JsonArray(); // Holds the current batch of devices

                int processedCount = 0; // Tracks the number of devices added to the batch

                for (JsonObject device : deviceQueue)
                {
                    // Retrieve last poll time and poll interval
                    var lastPollTime = device.getLong(Constants.LAST_POLL_TIME); // Default to 0L

                    // Check if the device's poll interval condition is satisfied
                    long timeSinceLastPoll = currentTime - lastPollTime;

                    if (timeSinceLastPoll >= pollInterval * 1000L)
                    {
                        // Update last poll time and add the device to the batch
                        device.put(Constants.LAST_POLL_TIME, currentTime);

                        batch.add(device);

                        processedCount++;

                        // When the batch size is reached, send the batch to the Poller Verticle
                        if (processedCount >= BATCH_SIZE)
                        {
                            sendBatchToPoller(batch, event);

                            LOGGER.debug("Batch sent for polling: {}", batch.encodePrettily());

                            batch = new JsonArray(); // Reset batch for the next 25 devices

                            processedCount = 0; // Reset counter
                        }
                    }
                }

                // Send any remaining devices in the batch
                if (!batch.isEmpty())
                {
                    sendBatchToPoller(batch, event);

                    LOGGER.debug("Batch sent for polling: {}", batch.encodePrettily());

                }

                if (processedCount == 0)
                {
                    LOGGER.info("No devices are ready for polling in this cycle.");
                }
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to schedule polling: {}", String.valueOf(exception));
        }
    }

    private void sendBatchToPoller(JsonArray batch, String event)
    {
        Main.vertx().eventBus().request(Constants.POLLER_VERTICLE, new JsonObject()
                        .put(Constants.DEVICES, batch)
                        .put(Constants.EVENT, event),
                new DeliveryOptions().setSendTimeout(5000),
                result ->
                {
                    if (result.succeeded())
                    {
                        LOGGER.info("Polling initiated successfully for the batch.");
                    }
                    else
                    {
                        LOGGER.error("Failed to initiate polling: {}", result.cause().getMessage());
                    }
                });
    }

    /* It fetches device details using objectId from the database */
    public Future<List<JsonObject>> fetch(JsonArray objectIds)
    {
        var promise = Promise.<List<JsonObject>>promise();

        try
        {
            Operations.findAll(Constants.OBJECTS_COLLECTION, new JsonObject().put(Constants.OBJECT_ID, new JsonObject().put("$in", objectIds))).onComplete(result ->
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
                    result.put(Constants.LAST_POLL_TIME, timestamp);

                    LOGGER.info("Provision result {} ", result.encodePrettily());

                    Operations.insert(Constants.POLLER_RESULTS_COLLECTION, result).onComplete(asyncResult ->
                    {
                        if(asyncResult.succeeded())
                        {
                            LOGGER.info("Poller result stored in database: {}", asyncResult.result());
                        }

                        else
                        {
                            LOGGER.error("Failed to store result {}", String.valueOf(asyncResult.cause()));
                        }
                    });
                }
            });
        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to store poller result. {}", String.valueOf(exception));
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
            LOGGER.error("Failed to check device availability. {}", String.valueOf(exception));

            return Future.failedFuture(exception);
        }

    }

    // Method that queries polling results from the database based on objectId or timestamp
    private void getPollerResult(Message<JsonObject> message)
    {
        var objectId = message.body().getString(Constants.OBJECT_ID);

        var timestamp = message.body().getLong(Constants.TIMESTAMP);

        var query = new JsonObject();

        // Build query based on provided objectId and/or timestamp
        if (objectId != null)
        {
            query.put(Constants.OBJECT_ID, objectId);
        }

        if (timestamp != null)
        {
            query.put(Constants.LAST_POLL_TIME, new JsonObject().put("$gte", timestamp));
        }

        // Reply immediately
        message.reply(new JsonObject().put("status", "Getting result"));

        // Perform database query
        Operations.findAll(Constants.POLLER_RESULTS_COLLECTION, query).onComplete(result ->
        {
            if (result.succeeded())
            {
                LOGGER.info("Polling results retrieved: ");

                Utils.parsePollingResults(result.result());
            }

            else
            {
                LOGGER.error("Error fetching polling results: {}", result.cause().getMessage());
            }
        });
    }

    // Method to handle object deletion
    private void deleteObject(Message<JsonObject> message)
    {
        var objectId = message.body().getString(Constants.OBJECT_ID);

        if (objectId == null || objectId.isEmpty())
        {
            message.fail(Constants.SC_400, "objectId is missing");

            return;
        }

        try
        {
            // Delete the object from the database
            var query = new JsonObject().put(Constants.OBJECT_ID, objectId);
            Operations.delete(Constants.OBJECTS_COLLECTION, query).onComplete(asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    LOGGER.info("Object with ID {} deleted successfully.", objectId);

                    message.reply("Object deleted successfully.");
                }
                else
                {
                    LOGGER.error("Failed to delete object with ID {}. {}", objectId, asyncResult.cause().getMessage());

                    message.fail(Constants.SC_500, "Failed to delete object: " + asyncResult.cause().getMessage());
                }
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error deleting object with ID {}. {}", objectId, exception.getMessage());

            message.fail(Constants.SC_500, "Error deleting object: " + exception.getMessage());
        }
    }
}
