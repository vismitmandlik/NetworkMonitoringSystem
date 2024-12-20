package com.motadata;

import com.motadata.services.Discovery;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import com.motadata.api.Server;
import com.motadata.db.Initializer;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main
{
    static Vertx vertx = Vertx.vertx();

    public static Vertx getVertxInstance()
    {
        return vertx;
    }

    public static void main(String[] args)
    {

        // Load configuration from config.json only once
        ConfigRetriever retriever = ConfigRetriever.create(vertx);

        // Suppress MongoDB logs
        var mongoLogger = Logger.getLogger("org.mongodb.driver");

        mongoLogger.setLevel(Level.OFF);

        retriever.getConfig(configResult ->
        {
            if (configResult.failed())
            {
                System.err.println("Failed to load configuration: " + configResult.cause());

                return;
            }

            var config = configResult.result();

            var initializer = new Initializer();

            initializer.initMongoClient(config);

            // Deploy Discovery Verticle with the configuration
            var discoveryOptions = new DeploymentOptions().setConfig(config);

            // Deploy Discovery Verticle
            vertx.deployVerticle(new Discovery(),discoveryOptions, response ->
            {
                if (response.succeeded())
                {
                    System.out.println("Successfully deployed Discovery Verticle");
                }
                else
                {
                    System.err.println("Failed to deploy Discovery Verticle: " + response.cause());
                }
            });

            // Deploy Server Verticle
            vertx.deployVerticle(Server.class.getName(),discoveryOptions, response ->
            {
                if (response.succeeded())
                {
                    System.out.println("Successfully deployed Server Verticle");
                }
                else
                {
                    System.err.println("Failed to deploy Server Verticle: " + response.cause());
                }
            });
        });
    }
}
