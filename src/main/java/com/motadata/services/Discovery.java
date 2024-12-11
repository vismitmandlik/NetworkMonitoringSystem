package com.motadata.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.net.NetClient;
import io.vertx.core.Vertx;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class Discovery extends AbstractVerticle {

    public static Vertx vertx = Vertx.vertx();

    static NetClient netClient = vertx.createNetClient();

    public static void discovery(RoutingContext context) {
        JsonObject requestBody = context.body().asJsonObject();
        String ipRange = requestBody.getString("ip");
        int port = requestBody.getInteger("port");
        String credentialsId = requestBody.getString("credentialsId");

        List<String> ips = extractIpAddresses(ipRange);

        for (String ip : ips) {
            pingIp(ip).compose(isReachable -> {
                if (isReachable) {
                    return checkPort(ip, port).compose(isOpen -> {
                        if (isOpen) {
                            return retrieveCredentials(credentialsId).compose(credentials -> {
                                return executeSshCommand(ip, port, credentials);
                            });
                        } else {
                            return Future.failedFuture("Port " + port + " is not open on " + ip);
                        }
                    });
                } else {
                    return Future.failedFuture("IP " + ip + " is not reachable.");
                }
            }).onComplete(ar -> {
                if (ar.succeeded()) {
                    context.response().setStatusCode(200).end("SSH command executed successfully.");
                } else {
                    context.response().setStatusCode(500).end("Error: " + ar.cause().getMessage());
                }
            });
        }
    }

    private static Future<Boolean> pingIp(String ip) {
        Promise<Boolean> promise = Promise.promise();
        vertx.executeBlocking(future -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "1", ip);
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                future.complete(exitCode == 0);
            } catch (Exception e) {
                future.fail(e);
            }
        }, promise);
        return promise.future();
    }

    private static Future<Boolean> checkPort(String ip, int port) {
        Promise<Boolean> promise = Promise.promise();
        netClient.connect(port, ip, res -> {
            if (res.succeeded()) {
                promise.complete(true);
            } else {
                promise.complete(false);
            }
        });
        return promise.future();
    }

    private static Future<JsonArray> retrieveCredentials(String credentialsId) {
        // Simulate database retrieval
        Promise<JsonArray> promise = Promise.promise();

        // Replace this with actual database call
        JsonArray credentials = new JsonArray()
                .add(new JsonObject().put("username", "user").put("password", "pass")); // Example credential

        promise.complete(credentials);

        return promise.future();
    }

    private static Future<Void> executeSshCommand(String ip, int port, JsonArray credentials) {
        return Future.future(promise -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();

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
        });
    }

    private static List<String> extractIpAddresses(String ipRange) {
        // Implement logic to extract IP addresses from range
        return List.of(ipRange.split(",")); // Simple split for demonstration
    }
}