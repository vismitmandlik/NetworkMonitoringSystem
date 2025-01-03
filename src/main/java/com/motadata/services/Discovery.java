package com.motadata.services;

import com.motadata.Main;
import com.motadata.constants.Constants;
import com.motadata.db.Operations;

import com.motadata.utils.Utils;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Discovery extends AbstractVerticle
{
    public static final String CREDENTIALS = "credentials";

    public static final String FAILED = "failed";

    public static final String REASON = "reason";

    private static final Logger LOGGER = LoggerFactory.getLogger(Discovery.class);
    
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

            var ipRange = requestBody.getString(Constants.IP);

            var port = requestBody.getInteger(Constants.PORT);

            var credentialsIds = requestBody.getJsonArray(Constants.CREDENTIALS_ID);

            var ips = Utils.extractIpAddresses(ipRange);

            var credentialsList = extractCredentials(credentialsIds);

            message.reply(new JsonObject().put(Constants.STATUS, "Discovery started"));

            for (var ip : ips)
            {
                // Create a result object for this IP
                var result = new JsonObject().put(Constants.IP, ip);

                var future = Utils.ping(ip).compose(isReachable ->
                {
                    if (isReachable)
                    {
                        return Utils.checkPort(ip, port).compose(isOpen ->
                        {
                            if (isOpen)
                            {
                                var ipCredentialObject = new JsonObject().put(Constants.IP, ip).put(Constants.PORT, port).put(CREDENTIALS, new JsonArray(credentialsList));

                                // Spawn Go process with IP
                                return spawnGoProcess(ipCredentialObject).map(successCredential ->
                                {
                                    if (successCredential != null)
                                    {
                                        // If SSH succeeded and returned a credential
                                        result.put(Constants.STATUS, Constants.SUCCESS);

                                        storeData(ip, port, successCredential);
                                    }

                                    else
                                    {
                                        result.put(Constants.STATUS, FAILED).put(REASON, "SSH failed");
                                    }

                                    return result;
                                });
                            }

                            else
                            {
                                result.put(Constants.STATUS, FAILED).put(REASON, "Port not open");

                                return Future.succeededFuture(result);
                            }
                        });
                    }

                    else
                    {
                        result.put(Constants.STATUS, FAILED).put(REASON, "IP not reachable");

                        return Future.succeededFuture(result);
                    }

                }).onFailure(err -> result.put(Constants.STATUS, FAILED).put(REASON, err.getMessage()));

                future.onComplete(AsyncResult ->
                {
                    if (AsyncResult.succeeded())
                    {
                        LOGGER.info("Discovery result for IP {}: {}", ip, AsyncResult.result().encodePrettily());
                    }

                    else
                    {
                        LOGGER.error("Error during discovery for IP {}: {}", ip, AsyncResult.cause().getMessage());
                    }
                });
            }
        }

        catch (Exception exception)
        {
            LOGGER.error("Error in discovery with exception : {}", String.valueOf(exception));
        }
    }

    private Future<JsonObject> retrieveCredentialById(String credentialsId)
    {
        // Simulate fetching a credential from the database based on the ID
        var promise = Promise.<JsonObject>promise();

        var query = new JsonObject().put(Constants.ID, credentialsId);

        try
        {
            // Return null if no credential found
            Operations.findOne(Constants.CREDENTIALS_COLLECTION, query)
                    .onComplete(result ->
                    {
                        if(result.succeeded())
                        {
                            promise.complete(result.result());
                        }

                        else
                        {
                            promise.fail(result.cause());
                        }
                    });
        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to retrieve credentials by id. {}", String.valueOf(exception));
        }

        return promise.future();
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
                    LOGGER.info("Successfully extracted credentials");
                }

                else
                {
                    System.err.println("Failed to extract credentials: " + asyncHandler.cause());
                }
            });
        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to extract credentials. {}", exception.getMessage());
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
                var goExecutable = vertx.getOrCreateContext().config().getString(Constants.GO_EXECUTABLE_PATH);

                if (goExecutable == null || goExecutable.isEmpty())
                {
                    throw new Exception("goExecutablePath is not set in the configuration.");
                }

                var processBuilder = new ProcessBuilder(goExecutable, Constants.DISCOVERY_EVENT, ipCredentialObject.encode()).directory(new File(config().getString(Constants.GO_EXECUTABLE_DIRECTORY)));

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

                LOGGER.info("Output: {}", output);

                var exitCode = process.waitFor();

                var result = new JsonObject(successfulCredential);

                if (exitCode == 0)
                {
                    LOGGER.info("Success credentials are: {}", result);

                }

                else
                {
                    var errorMessage = "Go process failed with exit code: " + exitCode;

                    LOGGER.error(errorMessage);

                }

                return result;

            }

            catch ( Exception exception)
            {
                LOGGER.error("Failed to spawn go process : {}", exception.getMessage());

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

                    catch (Exception exception)
                    {
                        LOGGER.error("Failed to close reader: {}", exception.getMessage());
                    }
                }

                if (process != null)
                {
                    process.destroy();
                }
            }
        },false);
    }

    private void storeData(String ip, int port, JsonObject credential)
    {
        // Query to check for duplicates
        var query = new JsonObject().put(Constants.IP, ip);

        try
        {
            Main.vertx().executeBlocking(() ->
            {
                // Check for existing entry
                Operations.findOne(Constants.OBJECTS_COLLECTION, query).onComplete(result ->
                {
                    if (result.result() == null)
                    {
                        // No duplicate found, insert the new data
                        var discoveryData = new JsonObject().put(Constants.IP, ip).put(CREDENTIALS, credential).put(Constants.PORT, port).put(Constants.OBJECT_ID, ip);

                        Operations.insert(Constants.OBJECTS_COLLECTION, discoveryData).onComplete(asyncResult ->
                        {
                            if (asyncResult != null)
                            {
                                LOGGER.info("Successfully stored discovery data: {}", discoveryData.encodePrettily());
                            }

                            else
                            {
                                LOGGER.error("Failed to store discovery data.");
                            }
                        });

                    }

                    else
                    {
                        LOGGER.info("Device rediscovered. Duplicate entry found for IP: {}, Port: {}", ip, port);
                    }
                });

                return null;
            },false, result ->
            {
                if (result.failed())
                {
                    LOGGER.error("Failed to store discovery data. {}", result.cause().getMessage());
                }
            });
        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to store Discovery Data");
        }
    }
}
