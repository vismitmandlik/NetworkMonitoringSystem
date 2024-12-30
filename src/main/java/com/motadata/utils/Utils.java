package com.motadata.utils;

import com.motadata.Main;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Utils
{

    public static Future<Boolean> ping(String ip)
    {
        return Main.vertx().executeBlocking(() ->
        {
            try
            {
                var processBuilder = new ProcessBuilder("ping", "-c", "1", ip);

                var process = processBuilder.start();

                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                var output = new StringBuilder();

                var line = "";

                while ((line = reader.readLine()) != null)
                {
                    output.append(line).append("\n");
                }

                var exitCode = process.waitFor();

                if (exitCode == 0)
                {
                    return true;
                }

                else
                {
                    System.err.println("Ping failed for " + ip + ":\n" + output);

                    return false;
                }
            }

            catch (Exception exception)
            {
                System.err.println("Failed to ping ip. " + exception);

                return false; // Return false on exception
            }
        });
    }

    public static ArrayList<String> extractIpAddresses(String ipRange)
    {
        var items = new ArrayList<String>();

        try
        {
            // Check if the input is a range (contains '-')
            if (ipRange.contains("-"))
            {
                var parts = ipRange.split("\\.");

                var baseIp = parts[0] + "." + parts[1] + "." + parts[2]; // Get first three octets

                var startOctet = parts[3].split("-")[0]; // Get starting octet

                var endOctet = parts[3].split("-")[1]; // Get ending octet

                var start = Integer.parseInt(startOctet);

                var end = Integer.parseInt(endOctet);

                // Generate IPs from baseIp + start to baseIp + end
                for (var i = start; i <= end; i++)
                {
                    items.add(baseIp + "." + i);
                }
            }

            else
            {
                // If not a range, just add the single IP
                items.add(ipRange.trim());
            }
        }

        catch (Exception exception)
        {
            System.err.println("Failed to extract ip addresses. " + exception);
        }

        return items;
    }

    public static Future<Boolean> checkPort( String ip, int port)
    {
        var promise = Promise.<Boolean>promise();

        try
        {
            Main.vertx().createNetClient().connect(port, ip, res ->
            {
                if (res.succeeded())
                {
                    promise.complete(true);
                }

                else
                {
                    System.err.println("Failed to connect to " + ip + ":" + port + " - " + res.cause().getMessage());

                    promise.complete(false);
                }
            });
        }
        catch (Exception exception)
        {
            System.err.println("Failed to check port. " + exception);

            promise.fail(exception);
        }

        return promise.future();
    }
}
