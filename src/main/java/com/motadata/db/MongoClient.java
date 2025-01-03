package com.motadata.db;

import com.motadata.Main;
import com.motadata.constants.Constants;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoClient
{
    public static final String DB_NAME = "db_name";

    public static final String MIN_MONGODB_POOL_SIZE = "minMongodbPoolSize";

    public static final String MAX_MONGODB_POOL_SIZE = "maxMongodbPoolSize";

    public static final int MIN_MONGODB_POOL_SIZE_VALUE = 3;

    public static final int MAX_MONGODB_POOL_SIZE_VALUE = 8;

    public static final String CONNECTION_STRING = "connection_string";

    public static final String CONNECTION_STRING_VALUE = "mongodb://localhost:27017";

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoClient.class);

    private static io.vertx.ext.mongo.MongoClient MONGO_CLIENT;

    public static Future<Void> init(JsonObject config)
    {
        var connectionString = config.getString(CONNECTION_STRING, CONNECTION_STRING_VALUE);

        var dbName = config.getString(DB_NAME, Constants.DB_NAME_VALUE);

        var minPoolSize = config.getInteger(MIN_MONGODB_POOL_SIZE, MIN_MONGODB_POOL_SIZE_VALUE);

        var maxPoolSize = config.getInteger(MAX_MONGODB_POOL_SIZE, MAX_MONGODB_POOL_SIZE_VALUE);

        LOGGER.info("Connecting to MongoDB...");

        /* Create a promise to track the success or failure of the connection */
        Promise<Void> promise = Promise.promise();

        try
        {
            /* Set minPoolSize = No. of Verticles and maxPoolSize = 100 {default} */
            MONGO_CLIENT = io.vertx.ext.mongo.MongoClient.createShared(Main.vertx(), new JsonObject().put("connection_string", connectionString).put("db_name", dbName).put("minPoolSize", minPoolSize).put("maxPoolSize", maxPoolSize));

            MONGO_CLIENT.runCommand("ping", new JsonObject().put("ping", 1), response ->
            {
                if (response.succeeded())
                {
                    promise.complete();
                }
                else
                {
                    LOGGER.error("Failed to connect: {}", response.cause().getMessage());

                    promise.fail(response.cause());
                }
            });
        }

        catch (Exception exception)
        {
            LOGGER.error("Failed to initialize Mongo client. {}", String.valueOf(exception));
        }

        return promise.future();
    }

    public static io.vertx.ext.mongo.MongoClient getMongoClient()
    {
        return MONGO_CLIENT;
    }
}
