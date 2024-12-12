package com.motadata.services;

import io.vertx.core.Vertx;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.net.NetClient;
import com.motadata.db.Operations;
import com.motadata.Main;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Discovery extends AbstractVerticle {

    private static NetClient netClient;

    static Vertx vertx = Main.getVertxInstance();

    @Override
    public void start(Promise<Void> startPromise) {
        startPromise.complete();
    }

    public static void discovery(RoutingContext context)
    {
        JsonObject requestBody = context.body().asJsonObject();

        String ipRange = requestBody.getString("ip");

        int port = requestBody.getInteger("port");

        JsonArray credentialsIds = requestBody.getJsonArray("credentialsIds");

        List<String> ips = extractIpAddresses(ipRange);

        List<JsonObject> credentialsList = extractCredentials(credentialsIds);

        List<Future> futures = new ArrayList<>(); // Store results for each IP

        for (String ip : ips)
        {
            // Create a result object for this IP
            JsonObject result = new JsonObject().put("ip", ip);

            Future<JsonObject> future = pingIp(ip).compose(isReachable ->
            {
                if (isReachable)
                {
                    return checkPort(ip, port).compose(isOpen ->
                    {
                        if (isOpen)
                        {
                            JsonObject ipCredentialObject = new JsonObject()
                                    .put("ip", ip)
                                    .put("port", port)
                                    .put("credentials", new JsonArray(credentialsList));

                            // Spawn Go process with IP and credentials
                            return spawnGoProcess(ipCredentialObject).map(success -> {
                                if (success) {
                                    result.put("status", "success");
                                } else {
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
            }).onFailure(err ->
            {
                result.put("status", "failed").put("reason", err.getMessage());
            });

            futures.add(future);
        }

        // Combine all futures and handle completion
        CompositeFuture.all(futures).onComplete(ar ->
        {
            JsonArray results = new JsonArray();

            for (Future<JsonObject> future : futures)
            {
                if (future.succeeded())
                {
                    results.add(future.result()); // Collect all successful results
                }
                else
                {
                    // If a future failed, add its result indicating failure
                    results.add(new JsonObject().put("ip", future.cause() != null ? future.cause().getMessage() : "Unknown error").put("status", "failed"));
                }
            }

            // Send response with all results
            context.response().setStatusCode(200).end(results.encodePrettily());
        });
    }

    private static Future<Boolean> pingIp(String ip)
    {
        Promise<Boolean> promise = Promise.promise();

        vertx.executeBlocking(future ->
        {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "1", ip);
                Process process = processBuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    future.complete(true);
                } else {
                    System.err.println("Ping failed for " + ip + ":\n" + output.toString());
                    future.complete(false);
                }
            } catch (Exception e) {
                future.fail(e);
            }
        }, promise);
        return promise.future();
    }

    private static Future<Boolean> checkPort(String ip, int port)
    {
        Promise<Boolean> promise = Promise.promise();

        netClient = vertx.createNetClient();

        netClient.connect(port, ip, res ->
        {
            if (res.succeeded())
            {
                promise.complete(true);
            }
            else
            {
                promise.complete(false);
            }
        });
        return promise.future();
    }

    private static JsonObject retrieveCredentialById(String credentialsId)
    {
        // Simulate fetching a credential from the database based on the ID
        Promise<JsonObject> promise = Promise.promise();

        JsonObject query = new JsonObject().put("_id", credentialsId);

        Operations.findOne("credentials", query).onSuccess(existingCredential -> {
            if (existingCredential != null) {
                promise.complete(existingCredential);
            } else {
                promise.complete(null); // Return null if no credential found
            }
        }).onFailure(err -> {
            promise.fail(err.getMessage());
        });

        return promise.future().result(); // This should be handled asynchronously in a real application
    }

    private static Future<Boolean> executeSshCommand(String ip, int port, JsonArray credentials)
    {
        return Future.future(promise ->
        {
            vertx.executeBlocking(future ->
            {
                try {
                    ProcessBuilder processBuilder = new ProcessBuilder();
                    processBuilder.directory(new java.io.File("/home/vismit/learning/new/Golang/GoSpawn/cmd"));

                    for (int i = 0; i < credentials.size(); i++) {
                        JsonObject cred = credentials.getJsonObject(i);
                        String username = cred.getString("username");
                        String password = cred.getString("password");

                        // Build the command with SSH and credentials
                        processBuilder.command("go", "run", "ssh_command.go", ip, String.valueOf(port), username, password);

                        Process process = processBuilder.start();

                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                        String line;

                        while ((line = reader.readLine()) != null) {
                            System.out.println(line); // Log output or handle as needed
                        }

                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            promise.complete(); // SSH successful
                            return; // Exit after success
                        }
                    }

                    promise.fail("SSH command failed for all credentials.");
                } catch (Exception e) {
                    promise.fail("Error executing SSH command: " + e.getMessage());
                }

            }, promise);
        });
    }

    private static List<String> extractIpAddresses(String ipRange)
    {
        List<String> ipList = new ArrayList<>();

        // Check if the input is a range (contains '-')
        if (ipRange.contains("-")) {
            String[] parts = ipRange.split("\\.");
            String baseIp = parts[0] + "." + parts[1] + "." + parts[2]; // Get first three octets
            String startOctet = parts[3].split("-")[0]; // Get starting octet
            String endOctet = parts[3].split("-")[1]; // Get ending octet

            int start = Integer.parseInt(startOctet);
            int end = Integer.parseInt(endOctet);

            // Generate IPs from baseIp + start to baseIp + end
            for (int i = start; i <= end; i++) {
                ipList.add(baseIp + "." + i);
            }
        } else {
            // If not a range, just add the single IP
            ipList.add(ipRange.trim());
        }

        return ipList;
    }

    private static List<JsonObject> extractCredentials(JsonArray credentialsIds)
    {
        List<JsonObject> credentialsList = new ArrayList<>();

        for (int i = 0; i < credentialsIds.size(); i++) {
            String credId = credentialsIds.getString(i);
            JsonObject credential = retrieveCredentialById(credId);
            if (credential != null) {
                credentialsList.add(credential);
            }
        }

        return credentialsList;
    }

    private static Future<Boolean> spawnGoProcess(JsonObject ipCredentialObject)
    {
        return Future.future(promise -> {
            vertx.executeBlocking(future -> {
                try {
                    ProcessBuilder processBuilder = new ProcessBuilder();
                    processBuilder.directory(new java.io.File("/home/vismit/vismit/learning/new/Golang/GoSpawn/cmd"));
                    processBuilder.command("go", "run", "ssh_command.go", ipCredentialObject.encode()); // Pass JSON encoded pairs

                    Process process = processBuilder.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    String line;
                    StringBuilder output = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n"); // Capture output from Go program
                    }

                    int exitCode = process.waitFor();

                    if (exitCode == 0) {
                        System.out.println(output.toString()); // Log output on success
                        future.complete(true); // Complete with success
                    } else {
                        System.err.println("Go process failed with exit code: " + exitCode);
                        future.complete(false); // Complete with failure
                    }
                } catch (Exception e) {
                    future.fail("Error starting Go process: " + e.getMessage());
                }
            }, promise); // Pass the original promise to complete it later
        });
    }


}
