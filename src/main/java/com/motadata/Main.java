package com.motadata;

import com.motadata.constants.Constants;
import com.motadata.services.Discovery;
import com.motadata.services.ObjectManager;
import com.motadata.services.Poller;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import com.motadata.api.Server;
import com.motadata.db.MongoClient;
import io.vertx.core.eventbus.EventBusOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    private static final int VERTX_WORKER_POOL_SIZE = 4;

    private static final int EVENT_BUS_CONNECTION_TIMEOUT = 30000;

    private static final int EVENT_BUS_IDLE_TIMEOUT = 120000;

    private static final Vertx VERTX = Vertx.vertx(new VertxOptions().setWorkerPoolSize(VERTX_WORKER_POOL_SIZE)
            .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors())
            .setEventBusOptions(new EventBusOptions().setConnectTimeout(EVENT_BUS_CONNECTION_TIMEOUT).setIdleTimeout(EVENT_BUS_IDLE_TIMEOUT)));

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final String CONFIG_FILE_PATH = "src/main/resources/config.json";

    private static final int DISCOVERY_VERTICLE_INSTANCES = 1;

    private static final int SERVER_VERTICLE_INSTANCES = 2;

    private static final int POLLER_VERTICLE_INSTANCES = 2;

    private static final int OBJECT_MANAGER_VERTICLE_INSTANCES = 2;

    public static Vertx vertx()
    {
        return VERTX;
    }

    public static void main(String[] args)
    {
        try
        {
            /* Set config.json path and load configuration from it */
            ConfigRetriever.create(VERTX, new io.vertx.config.ConfigRetrieverOptions()
                    .addStore(new ConfigStoreOptions()
                            .setType(Constants.FILE)
                            .setFormat(Constants.JSON)
                            .setConfig(new io.vertx.core.json.JsonObject().put(Constants.PATH, CONFIG_FILE_PATH))
                    )).getConfig(result ->
            {
                if (result.failed())
                {
                    LOGGER.error("Failed to load configuration: ", result.cause());

                    return;
                }

                var config = result.result();

                // Initialize MongoDB client and check if the connection is successful
                MongoClient.init(config).compose(mongoResponse ->
                {
                    var serverOptions = new DeploymentOptions().setConfig(config).setInstances(SERVER_VERTICLE_INSTANCES);

                    // Deploy Server Verticle first
                    return deployVerticle(Server.class.getName(), serverOptions);

                }).compose(serverResponse ->
                {
                    var discoveryOptions = new DeploymentOptions().setConfig(config).setInstances(DISCOVERY_VERTICLE_INSTANCES);

                    // Deploy Discovery Verticle
                    return deployVerticle(Discovery.class.getName(), discoveryOptions);

                }).compose(discoveryResponse ->
                {
                    var pollerOptions = new DeploymentOptions().setConfig(config).setInstances(POLLER_VERTICLE_INSTANCES);

                    // Deploy Poller Verticle
                    return deployVerticle(Poller.class.getName(), pollerOptions);

                }).compose(pollerResponse ->
                {
                    var objectManagerOptions = new DeploymentOptions().setConfig(config).setInstances(OBJECT_MANAGER_VERTICLE_INSTANCES);

                    // Deploy Poller Verticle
                    return deployVerticle(ObjectManager.class.getName(), objectManagerOptions);

                }).onComplete(response ->
                {
                    if (response.succeeded())
                    {
                        LOGGER.info("Successfully deployed all Verticles");
                    }
                    else
                    {
                        LOGGER.error("Failed to deploy Verticle: ", response.cause());

                        VERTX.close();
                    }
                });
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to deploy verticle: ", exception);
        }
    }

    /* Deploys a Verticle and returns a Future to track success or failure. */
    public static Future<Void> deployVerticle(String verticleName, DeploymentOptions options)
    {
        Promise<Void> promise = Promise.promise();

        try
        {
            VERTX.deployVerticle(verticleName, options, result ->
            {
                if (result.succeeded())
                {
                    promise.complete();
                }
                else
                {
                    promise.fail(result.cause());
                }
            });
        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to deploy verticle: {}", verticleName, exception);

            promise.fail(exception);
        }

        return promise.future();
    }
}