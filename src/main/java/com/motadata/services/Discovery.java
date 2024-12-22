package com.motadata.services;

import com.motadata.constants.Constants;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import com.motadata.db.Operations;
import com.motadata.Main;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Discovery extends AbstractVerticle
{
    @Override
    public void start()
    {
        vertx.eventBus().consumer(Constants.DISCOVERY_VERTICLE, this::discovery);
    }

    static Vertx vertx = Main.vertx();

    public void discovery(io.vertx.core.eventbus.Message<Object> message)
    {
        var requestBody = (JsonObject) message.body();

        var ipRange = requestBody.getString("ip");

        var port = requestBody.getInteger("port");

        var credentialsIds = requestBody.getJsonArray("credentialsIds");

        var ips = extractIpAddresses(ipRange);

        var credentialsList = extractCredentials(credentialsIds);

        var futures = new ArrayList<Future>(); // Store results for each IP

        message.reply(new JsonObject().put("status", "Discovery started"));

        for (var ip : ips)
        {
            // Create a result object for this IP
            var result = new JsonObject().put("ip", ip);

            var future = pingIp(ip).compose(isReachable ->
            {
                if (isReachable)
                {
                    return checkPort(ip, port).compose(isOpen ->
                    {
                        if (isOpen)
                        {
                            var ipCredentialObject = new JsonObject().put("ip", ip).put("port", port).put("credentials", new JsonArray(credentialsList));

                            // Spawn Go process with IP
                            return spawnGoProcess(ipCredentialObject).map(successCredential ->
                            {
                                if (successCredential != null)
                                {
                                    // If SSH succeeded and returned a credential
                                    result.put("status", "success");

                                    storeDiscoveryData(ip, port, successCredential);
                                }
                                else
                                {
                                    result.put("status", "failed").put("reason", "SSH failed");
                                }
                                return result;
                            });
                        }
                        else
                        {
                            result.put("status", "failed").put("reason", "Port not open");

                            return Future.succeededFuture(result);
                        }
                    });
                }
                else
                {
                    result.put("status", "failed").put("reason", "IP not reachable");

                    return Future.succeededFuture(result);
                }
            }).onFailure(err -> result.put("status", "failed").put("reason", err.getMessage()));

            futures.add(future);

            future.onComplete(AsyncResult ->
            {
                if (AsyncResult.succeeded())
                {
                    System.out.println("Discovery result for IP " + ip + ": " + AsyncResult.result().encodePrettily());
                }
                else
                {
                    System.err.println("Error during discovery for IP " + ip + ": " + AsyncResult.cause().getMessage());
                }
            });
        }
    }

    private static Future<Boolean> pingIp(String ip)
    {
        return vertx.executeBlocking(() ->
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
                // Handle exception
                exception.printStackTrace();

                return false; // Return false on exception
            }
        });
    }

    private static Future<Boolean> checkPort(String ip, int port)
    {
        Promise<Boolean> promise = Promise.promise();

        vertx.createNetClient().connect(port, ip, res ->
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

        return promise.future();
    }

    private static Future<JsonObject> retrieveCredentialById(String credentialsId)
    {
        // Simulate fetching a credential from the database based on the ID
        var promise = Promise.<JsonObject>promise();

        var query = new JsonObject().put("_id", credentialsId);

        // Return null if no credential found
        Operations.findOne(Constants.CREDENTIALS_COLLECTION, query).onSuccess(promise::complete).onFailure(err -> promise.fail(err.getMessage()));

        return promise.future(); // This should be handled asynchronously in a real application
    }

    private static ArrayList<String> extractIpAddresses(String ipRange)
    {
        var ipList = new ArrayList<String>();

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
                ipList.add(baseIp + "." + i);
            }
        }
        else
        {
            // If not a range, just add the single IP
            ipList.add(ipRange.trim());
        }

        return ipList;
    }

    private static List<JsonObject> extractCredentials(JsonArray credentialsIds)
    {
        var credentialsList = new ArrayList<JsonObject>();

        var futures = new ArrayList<Future>();

        // Using executeBlocking to handle blocking code
        vertx.executeBlocking(blockingPromise ->
        {
            for (var i = 0; i < credentialsIds.size(); i++)
            {
                var credId = credentialsIds.getString(i);

                var credentialFuture = retrieveCredentialById(credId);

                futures.add(credentialFuture);

                credentialFuture.onSuccess(credential ->
                {
                    if (credential != null)
                    {
                        credentialsList.add(credential);
                    }
                });
            }

            CompositeFuture.all(futures).onComplete(res ->
            {
                if (res.succeeded())
                {
                    blockingPromise.complete();
                }
                else
                {
                    blockingPromise.fail(res.cause());
                }
            });
        }, false, res ->
        {
            if (res.succeeded())
            {
                System.out.println("Successfully extracted credentials");
            }
            else
            {
                System.err.println("Failed to extract credentials: " + res.cause());
            }
        });

        return credentialsList;
    }

    private static Future<JsonObject> spawnGoProcess(JsonObject ipCredentialObject)
    {
        return vertx.executeBlocking(promise ->
        {
            try
            {

                var goExecutable = Main.vertx().getOrCreateContext().config().getString("goExecutablePath");

                var processBuilder = new ProcessBuilder(goExecutable, Constants.DISCOVERY_EVENT, ipCredentialObject.encode()).directory(new java.io.File("/home/vismit/vismit/learning/new/Golang/GoSpawn/cmd"));

                var process = processBuilder.start();

                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                var output = new StringBuilder();

                var line = "";

                var successfulCredential = "";

                while ((line = reader.readLine()) != null)
                {
                    output.append(line).append("\n"); // Capture output from Go program

                    // Check for the success message that contains credentials
                    if (line.contains("Successful login for IP"))
                    {
                        // Extract the credentials from the line (e.g., after the colon)
                        var credentialsString = line.substring(line.indexOf("{"));

                        successfulCredential = credentialsString.trim();
                    }
                }

                System.out.println("Output: " + output);

                var exitCode = process.waitFor();

                if (exitCode == 0 )
                {
                    var result = new JsonObject(successfulCredential);

                    System.out.println("Success credentials are : " + result );

                    promise.complete(result); // Return the successful credential as JsonObject
                }

                else
                {
                    System.err.println("Go process failed with exit code: " + exitCode);

                    promise.fail("Go process failed with exit code: " + exitCode);
                }
            }
            catch (Exception exception)
            {
                System.err.println("Error starting Go process: " + exception.getMessage());

                promise.fail(exception);
            }
        });
    }

    private static void storeDiscoveryData(String ip, int port, JsonObject credential)
    {
        // Query to check for duplicates
        var query = new JsonObject().put("ip", ip);

        // Check for existing entry
        Operations.findOne(Constants.OBJECTS_COLLECTION, query).onSuccess(existingEntry ->
        {
            if (existingEntry == null)
            {
                // No duplicate found, insert the new data
                var discoveryData = new JsonObject().put("ip", ip).put("credentials", credential).put("port", port);

                Operations.insert(Constants.OBJECTS_COLLECTION, discoveryData).onSuccess(result -> System.out.println("Successfully stored discovery data: " + discoveryData.encodePrettily())).onFailure(err -> System.err.println("Failed to store discovery data: " + err.getMessage()));
            }
            else
            {
                System.out.println("Duplicate entry found for IP: " + ip + ", Port: " + port);
            }

        }).onFailure(err -> System.err.println("Failed to check for duplicate entry: " + err.getMessage()));
    }

}
