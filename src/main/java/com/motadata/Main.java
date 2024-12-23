package com.motadata;

import com.motadata.services.Discovery;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import com.motadata.api.Server;
import com.motadata.db.MongoClient;
import io.vertx.core.VertxOptions;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main
{
    private static Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(Runtime.getRuntime().availableProcessors() * 2));

    public static Vertx vertx()
    {
        return vertx;
    }

    public static void main(String[] args)
    {
        // Suppress MongoDB logs
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
            
            MongoClient.init(config);

            var discoveryOptions = new DeploymentOptions().setConfig(config);



            // Deploy Server Verticle
            vertx.deployVerticle(Server.class.getName(),discoveryOptions, serverResponse ->
            {
                if (serverResponse.succeeded())
                {
                    System.out.println("Successfully deployed Server Verticle");

                    // Deploy Discovery Verticle
                    vertx.deployVerticle(Discovery.class.getName(),discoveryOptions, discoveryResponse ->
                    {
                        if (discoveryResponse.succeeded())
                        {
                            System.out.println("Successfully deployed Discovery Verticle");
                        }
                        else
                        {
                            System.err.println("Failed to deploy Discovery Verticle: " + discoveryResponse.cause());
                        }
                    });
                }
                else
                {
                    System.err.println("Failed to deploy Server Verticle: " + serverResponse.cause());
                }
            });
        });
    }
}
