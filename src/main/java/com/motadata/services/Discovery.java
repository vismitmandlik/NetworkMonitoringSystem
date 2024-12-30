package com.motadata.services;

import com.motadata.constants.Constants;
import com.motadata.db.Operations;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Discovery extends AbstractVerticle
{
    @Override
    public void start()
    {
        vertx.eventBus().localConsumer(Constants.DISCOVERY_VERTICLE, this::discovery);
    }

    public void discovery(Message<Object> message)
    {
        try
        {
            var requestBody = (JsonObject) message.body();

            var ipRange = requestBody.getString("ip");

            var port = requestBody.getInteger("port");

            var credentialsIds = requestBody.getJsonArray("credentialsIds");

            var ips = extractIpAddresses(ipRange);

            var credentialsList = extractCredentials(credentialsIds);

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

        catch (Exception exception)
        {
            System.err.println("Error in discovery with exception : " + exception);
        }
    }

    private Future<Boolean> pingIp(String ip)
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
                System.err.println("Failed to ping ip. " + exception);

                return false; // Return false on exception
            }
        });
    }

    private Future<Boolean> checkPort(String ip, int port)
    {
        var promise = Promise.<Boolean>promise();

        try
        {
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
        }
        catch (Exception exception)
        {
            System.err.println("Failed to check port. " + exception);

            promise.fail(exception);
        }

        return promise.future();
    }

    private Future<JsonObject> retrieveCredentialById(String credentialsId)
    {
        // Simulate fetching a credential from the database based on the ID
        var promise = Promise.<JsonObject>promise();

        var query = new JsonObject().put("_id", credentialsId);

        try
        {
            // Return null if no credential found
            Operations.findOne(Constants.CREDENTIALS_COLLECTION, query)
                    .onComplete(result ->
                    {
                        if(result.succeeded())
                        {
                            promise.complete((JsonObject) result);
                        }

                        else
                        {
                            promise.fail(result.cause());
                        }
                    });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to retrieve credentials by id. " + exception);
        }

        return promise.future();
    }

    private static ArrayList<String> extractIpAddresses(String ipRange)
    {
        var ipList = new ArrayList<String>();

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
                    ipList.add(baseIp + "." + i);
                }
            }

            else
            {
                // If not a range, just add the single IP
                ipList.add(ipRange.trim());
            }
        }

        catch (Exception exception)
        {
            System.err.println("Failed to extract ip addresses. " + exception);
        }

        return ipList;
    }

    private List<JsonObject> extractCredentials(JsonArray credentialsIds)
    {
        var credentialsList = new ArrayList<JsonObject>();

        var futures = new ArrayList<Future<JsonObject>>();

        try
        {
            // Using executeBlocking to handle blocking database operations
            vertx.executeBlocking(() ->
            {
                for (var i = 0; i < credentialsIds.size(); i++)
                {
                    var credId = credentialsIds.getString(i);

                    var credentialFuture = retrieveCredentialById(credId);

                    futures.add(credentialFuture);

                    credentialFuture.onComplete(result ->
                    {
                        if (result != null)
                        {
                            credentialsList.add(result.result());
                        }
                    });
                }
                return Future.all(futures);
            }, false, asyncHandler ->
            {
                if (asyncHandler.succeeded())
                {
                    System.out.println("Successfully extracted credentials");
                }

                else
                {
                    System.err.println("Failed to extract credentials: " + asyncHandler.cause());
                }
            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to extract credentials. " + exception);
        }

        return credentialsList;
    }

    private Future<JsonObject> spawnGoProcess(JsonObject ipCredentialObject)
    {
        return vertx.executeBlocking(() ->
        {
            Process process = null;

            BufferedReader reader = null;

            var successfulCredential = "";

            try
            {
                var goExecutable = vertx.getOrCreateContext().config().getString("goExecutablePath");

                if (goExecutable == null || goExecutable.isEmpty())
                {
                    throw new Exception("goExecutablePath is not set in the configuration.");
                }

                var processBuilder = new ProcessBuilder(goExecutable, Constants.DISCOVERY_EVENT, ipCredentialObject.encode()).directory(new File("/home/vismit/vismit/learning/new/Golang/GoSpawn/cmd/"));

                process = processBuilder.start();

                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                var output = new StringBuilder();

                var line = "";

                while ((line = reader.readLine()) != null)
                {
                    output.append(line).append("\n");

                    if (line.contains("Successful login for IP"))
                    {
                        var startIndex = line.indexOf("{");

                        if (startIndex != -1)
                        {
                            successfulCredential = line.substring(startIndex).trim();
                        }
                    }
                }

                System.out.println("Output: " + output);

                var exitCode = process.waitFor();

                var result = new JsonObject(successfulCredential);

                if (exitCode == 0)
                {
                    System.out.println("Success credentials are: " + result);

                }

                else
                {
                    var errorMessage = "Go process failed with exit code: " + exitCode;

                    System.err.println(errorMessage);

                }
                
                return result;

            }

            catch ( Exception exception)
            {
                System.err.println("Error starting Go process: " + exception.getMessage());

                return null;
            }

            finally
            {
                if (reader != null)
                {
                    try
                    {
                        reader.close();
                    }

                    catch (IOException e)
                    {
                        System.err.println("Failed to close reader: " + e.getMessage());
                    }
                }

                if (process != null)
                {
                    process.destroy();
                }
            }
        });
    }

    private void storeDiscoveryData(String ip, int port, JsonObject credential)
    {
        // Query to check for duplicates
        var query = new JsonObject().put("ip", ip);

        try
        {
            // Check for existing entry
            Operations.findOne(Constants.OBJECTS_COLLECTION, query).onComplete(result ->
            {
                if (result == null)
                {
                    // No duplicate found, insert the new data
                    var discoveryData = new JsonObject().put("ip", ip).put("credentials", credential).put("port", port);

                    Operations.insert(Constants.OBJECTS_COLLECTION, discoveryData).onComplete(asyncResult ->
                    {
                        if (asyncResult != null)
                        {
                            System.out.println("Successfully stored discovery data: " + discoveryData.encodePrettily());
                        }

                        else
                        {
                            System.err.println("Failed to store discovery data.");
                        }
                    });
                }

                else
                {
                    System.out.println("Duplicate entry found for IP: " + ip + ", Port: " + port);
                }

            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to store Discovery Data");
        }
    }
}
