package com.motadata.services;

import com.motadata.Main;
import com.motadata.constants.Constants;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Poller extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Poller.class);

    public void start()
    {
        vertx.eventBus().localConsumer(Constants.POLLER_VERTICLE, this::poll);
    }

    public void poll(Message<JsonObject> message)
    {
        var requestBody = message.body();

        var device = requestBody.getJsonArray(Constants.DEVICES);

        var event = requestBody.getString(Constants.EVENT);

        Main.vertx().executeBlocking(()  ->
        {
            Process process = null;

            try
            {
                if (device == null || device.isEmpty())
                {
                    LOGGER.error("Device is null or empty.");

                    message.fail(Constants.SC_400,"Devices array is null or empty.");

                    return false;
                }

                if (event == null || event.isEmpty())
                {
                    LOGGER.error("Event is null or empty.");

                    message.fail(Constants.SC_400,"Event is null or empty.");

                    return false;
                }

                var devicesJsonString = device.encode();

                LOGGER.debug("Devices string: {}", devicesJsonString);

                var goExecutable = Main.vertx().getOrCreateContext().config().getString(Constants.GO_EXECUTABLE_PATH);

                // Check if goExecutable is null
                if (goExecutable == null)
                {
                    LOGGER.error("goExecutablePath is null and not set in the configuration.");

                    message.fail(Constants.SC_500,"Go executable path is not configured.");

                    return false;
                }

                // Start the external process
                process = new ProcessBuilder(goExecutable, event, devicesJsonString).directory(new File("/home/vismit/vismit/learning/new/Golang/GoSpawn/cmd")).start();

                var outputLines = new JsonArray();

                // Use try-with-resources to handle BufferedReader
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
                {
                    var line = "";

                    while ((line = reader.readLine()) != null)
                    {
                        outputLines.add(line);
                    }
                }

                process.waitFor(Main.vertx().getOrCreateContext().config().getInteger(Constants.POLLER_TIMEOUT), TimeUnit.SECONDS);

                // Print polling result
                if (process.exitValue() == 0)
                {
                    LOGGER.info("Polling result for devices: {}", outputLines.encodePrettily());

                    ObjectManager.store(outputLines);

                    // Send reply if needed
                    message.reply(new JsonObject().put(Constants.STATUS, Constants.SUCCESS).put(Constants.MESSAGE, "Polling successful"));

                    return true;
                }

                else
                {
                    LOGGER.error("Polling failed for devices.");

                    message.fail(Constants.SC_500, "Polling failed");

                    return false;
                }
            }

            catch (Exception exception)
            {
                LOGGER.error("Failed to poll device: {}", exception.getMessage());

                message.fail(Constants.SC_500,"Exception during polling: ");

                return false;
            }

            finally
            {
                // Ensure the process is destroyed if it's still running
                if (process != null && process.isAlive())
                {
                    process.destroy();

                    LOGGER.debug("Polling process for devices was destroyed.");
                }

                LOGGER.debug("Poll process completed.");
            }

        }, false, asyncHandler ->
        {
            if (asyncHandler.succeeded())
            {
                LOGGER.debug("Poll completed successfully");

                message.reply(asyncHandler.result());
            }

            else
            {
                LOGGER.error("Poll failed with error: {}", asyncHandler.cause().getMessage());

                message.fail(Constants.SC_500, asyncHandler.cause().getMessage());
            }
        });
    }
}
