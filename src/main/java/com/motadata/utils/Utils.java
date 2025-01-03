package com.motadata.utils;

import com.motadata.Main;
import com.motadata.constants.Constants;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
public class Utils
{

    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

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
                    LOGGER.error("Ping failed for {}:\n{}", ip, output);

                    return false;
                }
            }

            catch (Exception exception)
            {
                LOGGER.error("Failed to ping IP {}: {}", ip, exception.getMessage());

                return false; // Return false on exception
            }
        },false);
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
            LOGGER.error("Failed to extract IP addresses from range {}: {}", ipRange, exception.getMessage());
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
                    LOGGER.error("Failed to connect to {}:{} - {}", ip, port, res.cause().getMessage());

                    promise.complete(false);
                }
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to check port on {}:{} - {}", ip, port, exception.getMessage());

            promise.fail(exception);
        }

        return promise.future();
    }

    public static JsonObject parsePollerResult(String pollerResult)
    {
        try
        {
            var result = new JsonObject(pollerResult);

            var objectId = result.getString(Constants.OBJECT_ID);

            if (objectId == null || objectId.isEmpty())

            {
                LOGGER.error("objectId is null or empty in result: {}", result);

                return null;
            }

            return new JsonObject()
                    .put("objectId", objectId)
                    .put("ip", result.getString("ip"))
                    .put("cpuUsage", parseDouble(result.getString("cpuUsage")))
                    .put("memoryUsage", parseDouble(result.getString("memoryUsage")))
                    .put("diskUsage", parseDouble(result.getString("diskUsage").replace("%", "")));
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to parse poller result: {} - {}", pollerResult, exception.getMessage());

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
            LOGGER.warn("Failed to parse value to double: {}", value);

            return 0.0;
        }
    }

    public static String successResponse()
    {
        return new JsonObject().put("message", "Polling tasks started for provisioned devices").toString();
    }

    public static boolean isValidRequest(JsonObject requestBody)
    {
        return requestBody != null && requestBody.containsKey(Constants.OBJECT_IDS) && requestBody.containsKey(Constants.POLL_INTERVAL);
    }

    public static void parsePollingResults(List<JsonObject> pollingResults)
    {
        try
        {
            // Iterate over each polling result
            for (JsonObject result : pollingResults)
            {
                // Extract lastPollTime and convert to human-readable format
                var lastPollTime = result.getLong("lastPollTime");

                var formattedTime = convertTimestampToActualTime(lastPollTime);

                // Print the simplified result
                LOGGER.info("Object ID: {}", result.getString(Constants.OBJECT_ID));
                LOGGER.info("IP: {}", result.getString(Constants.IP));
                LOGGER.info("CPU Usage: {}", result.getDouble(Constants.CPU_USAGE));
                LOGGER.info("Memory Usage: {}", result.getDouble(Constants.MEMORY_USAGE));
                LOGGER.info("Disk Usage: {}", result.getDouble(Constants.DISK_USAGE));
                LOGGER.info("Last Poll Time: {}", formattedTime);
                LOGGER.info("-----");
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to parse polling results {} ", exception.getMessage());
        }

    }

    public static String convertTimestampToActualTime(long timestamp)
    {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
    }
}
