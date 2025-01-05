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

        private static final int BATCH_SIZE = 25;

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

                var batch = new JsonArray();

                var successfulDevices = new JsonArray();

                // Create an ArrayList to collect the futures
                var futures = new ArrayList<Future<JsonObject>>();

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
                                    var deviceDetails = new JsonObject().put(Constants.IP, ip).put(Constants.PORT, port).put(CREDENTIALS, new JsonArray(credentialsList));

                                    // Add to batch
                                    batch.add(deviceDetails);

                                    // If batch size exceeds the threshold (25), spawn the Go process and reset the batch
                                    if (batch.size() >= BATCH_SIZE)
                                    {
                                        spawnGoProcess(batch).onComplete(asyncResult ->
                                        {
                                            if (asyncResult.succeeded())
                                            {
                                                LOGGER.info("Go process for batch completed successfully.");

                                                successfulDevices.addAll(getSuccessfulDevices(batch));  // Store only successful devices
                                            }
                                            else
                                            {
                                                LOGGER.error("Go process for batch failed.");
                                            }
                                        });

                                        batch.clear();  // Reset batch after processing
                                    }

                                }

                                else
                                {
                                    result.put(Constants.STATUS, FAILED).put(REASON, "Port not open");

                                }
                                return Future.succeededFuture(result);
                            });
                        }

                        else
                        {
                            result.put(Constants.STATUS, FAILED).put(REASON, "IP not reachable");

                            return Future.succeededFuture(result);
                        }

                    }).onFailure(err -> result.put(Constants.STATUS, FAILED).put(REASON, err.getMessage()));

                    futures.add(future);
                }

                Future.all(futures).onComplete(asyncResult ->
                {
                    if (asyncResult.succeeded())
                    {
                        // If there are any remaining devices after the loop ends (i.e., batch < 25), process them
                        if (!batch.isEmpty())
                        {
                            var future = spawnGoProcess(batch).onComplete(result ->
                            {
                                if (result.succeeded())
                                {
                                    LOGGER.info("Go process for remaining batch completed successfully.");

                                    successfulDevices.addAll(getSuccessfulDevices(batch));  // Add remaining successful devices
                                }
                                else
                                {
                                    LOGGER.error("Go process for remaining batch failed.");
                                }

                                // After processing all devices, store only the successful ones
                                storeData(successfulDevices);  // Store only the devices that succeeded
                            });

                            futures.add(future);
                        }

                        LOGGER.info(asyncResult.result().toString());

                        var results = asyncResult.result().list(); // Get the list of results

                        for (var i = 0; i < ips.size(); i++)
                        {
                            var ip = ips.get(i);

                            var result = results.get(i);

                            LOGGER.info("Discovery result for IP {}: {}", ip, result);
                        }
                    }

                    else
                    {
                        LOGGER.error("Error during discovery {}", asyncResult.cause().getMessage());
                    }
                });
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

            // Create a promise to return the list of credentials
            var promise = Promise.<List<JsonObject>>promise();

            try
            {
                // Using executeBlocking to handle blocking database operations
                vertx.executeBlocking(() ->
                {
                    for (var i = 0; i < credentialsIds.size(); i++)
                    {
                        var credentialId = credentialsIds.getString(i);

                        var credentialFuture = retrieveCredentialById(credentialId);

                        futures.add(credentialFuture);

                        credentialFuture.onComplete(result ->
                        {
                            if (result.succeeded())
                            {
                                credentialsList.add(result.result());
                            }
                            else
                            {
                                LOGGER.error("Failed to retrieve credential for ID {}: {}", credentialId, result.cause().getMessage());
                            }
                        });
                    }

                    Future.all(futures).onComplete(allResult ->
                    {
                        if (allResult.succeeded()) {
                            LOGGER.info("Successfully extracted credentials");
                            promise.complete(credentialsList); // Complete with the list
                        } else {
                            LOGGER.error("Failed to extract all credentials: {}", allResult.cause().getMessage());
                            promise.fail(allResult.cause()); // Fail the promise if something goes wrong
                        }
                    });

                    return promise.future();

                }, false, asyncHandler ->
                {
                    if (asyncHandler.succeeded())
                    {
                        LOGGER.info(asyncHandler.result().toString());

//                        LOGGER.info("Successfully extracted credentials");

//                        futures.complete(credentialsList);

                    }

                    else
                    {
                        LOGGER.error("Failed to extract credentials: {}", asyncHandler.cause().getMessage());

//                        resultFuture.fail("Failed to extract credentials");  // Fail the future if something goes wrong
                    }
                });
            }

            catch (Exception exception)
            {
                LOGGER.error("Failed to extract credentials. {}", exception.getMessage());
            }

            return credentialsList;
        }

        private Future<JsonObject> spawnGoProcess(JsonArray batch)
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

                    LOGGER.info("Attempting to spawn Go process with batch: {}", batch.encode());

                    var processBuilder = new ProcessBuilder(goExecutable, Constants.DISCOVERY_EVENT, batch.encode()).directory(new File(config().getString(Constants.GO_EXECUTABLE_DIRECTORY)));

                    process = processBuilder.start();

                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    var output = new StringBuilder();

                    var line = "";

                    while ((line = reader.readLine()) != null)
                    {
                        output.append(line).append("\n");

                        LOGGER.info("Raw Go output: {}", line);

                        if (line.contains("IP success, Credentials"))
                        {
                            var startIndex = line.indexOf("{");

                            if (startIndex != -1)
                            {
                                successfulCredential = line.substring(startIndex).trim();
                            }
                        }
                    }

                    LOGGER.info("Output------: {}", output);

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
                    LOGGER.debug("Failed to spawn go process : {}", exception.getMessage());

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

        private void storeData(JsonArray successfulDevices)
        {
            try
            {
                Main.vertx().executeBlocking(() ->
                {
                    for (var deviceObj : successfulDevices)
                    {

                        var device = (JsonObject) deviceObj;

                        var ip = device.getString(Constants.IP);

                        // Query to check for duplicates
                        var query = new JsonObject().put(Constants.IP, ip);

                        // Check for existing entry
                        Operations.findOne(Constants.OBJECTS_COLLECTION, query).onComplete(result ->
                        {
                            if (result.result() == null)
                            {
                                // No duplicate found, insert the new data
                                var discoveryData = new JsonObject().put(Constants.IP, ip).put(CREDENTIALS, device.getJsonArray(CREDENTIALS)).put(Constants.PORT, device.getInteger(Constants.PORT)).put(Constants.OBJECT_ID, ip);

                                Operations.insert(Constants.OBJECTS_COLLECTION, discoveryData).onComplete(asyncResult ->
                                {
                                    if (asyncResult.succeeded())
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
                                LOGGER.info("Device rediscovered. Duplicate entry found for IP: {}, Port: {}", ip, device.getInteger(Constants.PORT));
                            }
                        });
                    }
                    return null;
                    }, false, result ->
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

        // New helper function to filter successful devices
        private JsonArray getSuccessfulDevices(JsonArray batch)
        {
            JsonArray successfulDevices = new JsonArray();

            for (var deviceObj : batch)
            {
                JsonObject device = (JsonObject) deviceObj;

                if (device.containsKey("credentials"))
                {
                    successfulDevices.add(device);
                }
            }
            return successfulDevices;
        }
    }
