package com.motadata.services;

import com.motadata.Main;
import io.vertx.core.json.JsonArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import static com.motadata.services.ObjectManager.storePollerResults;

public class Poller
{
    public static void pollDevice(JsonArray device, String event)
    {
        Main.vertx().executeBlocking(()  ->
        {
            Process process = null;

            try
            {
                if (device == null || device.isEmpty())
                {
                    System.err.println("Device is null or empty.");

                    return false;
                }

                var devicesJsonString = new JsonArray().add(device).encode();

                var goExecutable = Main.vertx().getOrCreateContext().config().getString("goExecutablePath");

                // Check if goExecutable is null
                if (goExecutable == null)
                {
                    System.err.println("goExecutablePath is null and not set in the configuration.");

                    return false;
                }

                // Start the external process
                process = new ProcessBuilder(goExecutable, event, devicesJsonString).directory(new File("/home/vismit/vismit/learning/new/Golang/GoSpawn/cmd")).start();

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

                process.waitFor(Main.vertx().getOrCreateContext().config().getInteger("pollerTimeout"), TimeUnit.SECONDS);

                // Print polling result
                if (process.exitValue() == 0)
                {
                    System.out.println("Polling result for devices: "  + outputLines.encodePrettily());

                    storePollerResults(outputLines);

                    return true;
                }

                else
                {
                    System.out.println("Polling failed for devices " );

                    return false;
                }
            }

            catch (Exception exception)
            {
                System.err.println("Failed to poll device. " + exception);

                return false;
            }

            finally
            {
                // Ensure the process is destroyed if it's still running
                if (process != null && process.isAlive())
                {
                    process.destroy();

                    System.out.println("Polling process for devices was destroyed.");
                }

                System.out.println("Poll process completed");
            }

        }, false, asyncHandler ->
        {
            if (asyncHandler.succeeded())
            {
                System.out.println("Poll completed successfully");
            }

            else
            {
                System.err.println("Poll failed with error: " + asyncHandler.cause());
            }
        });
    }
}
