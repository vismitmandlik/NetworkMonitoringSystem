package com.motadata.services;

import com.motadata.Main;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static com.motadata.services.ObjectManager.storePollerResults;

public class Poller
{
    public static void pollDevice(JsonObject device, String event)
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
}
