package com.motadata;

import com.motadata.services.Discovery;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import com.motadata.api.Server;
import com.motadata.db.MongoClient;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main
{
    private static final Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(Runtime.getRuntime().availableProcessors() * 2));

    public static Vertx vertx()
    {
        return vertx;
    }

    public static void main(String[] args)
    {
        /* Suppress MongoDB logs warning */
        var mongoLogger = Logger.getLogger("org.mongodb.driver");

        mongoLogger.setLevel(Level.OFF);

        /* Set config.json path and load configuration from it */
        ConfigRetriever.create(vertx, new io.vertx.config.ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                        .setType("file")
                        .setFormat("json")
                        .setConfig(new io.vertx.core.json.JsonObject().put("path", "src/main/resources/config.json"))
                )).getConfig(configResult ->
        {
            if (configResult.failed())
            {
                System.err.println("Failed to load configuration: " + configResult.cause());

                return;
            }

            var config = configResult.result();

            System.out.println("Configuration: " + config.encodePrettily());

            // Initialize MongoDB client and check if the connection is successful
            MongoClient.init(config).compose(initResult ->
            {
                System.out.println("Successfully connected to MongoDB");

                var serverOptions = new DeploymentOptions().setConfig(config).setInstances(Runtime.getRuntime().availableProcessors() * 2);

                // Deploy Server Verticle first
                return deployVerticle(Server.class.getName(), serverOptions);

            }).compose(serverResponse ->
            {
                System.out.println("Successfully deployed Server Verticle");

                var discoveryOptions = new DeploymentOptions().setConfig(config).setInstances(Runtime.getRuntime().availableProcessors() * 2);

                // Deploy Discovery Verticle
                return deployVerticle(Discovery.class.getName(), discoveryOptions);

            }).onSuccess(discoveryResponse -> System.out.println("Successfully deployed Discovery Verticle")).onFailure(err ->
            {
                System.err.println("Failed to deploy Verticle: " + err.getMessage());

                vertx.close();
            });
        });

    }

    /* Deploys a Verticle and returns a Future to track success or failure. */
    public static Future<Void> deployVerticle(String verticleName, DeploymentOptions options)
    {
        Promise<Void> promise = Promise.promise();

        vertx.deployVerticle(verticleName, options, deployResult ->
        {
            if (deployResult.succeeded())
            {
                promise.complete();
            }
            else
            {
                promise.fail(deployResult.cause());
            }
        });

        return promise.future();
    }
}