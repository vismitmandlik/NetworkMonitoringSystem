package com.motadata.db;

import com.motadata.Main;
import com.motadata.constants.Constants;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class MongoClient
{
    private static io.vertx.ext.mongo.MongoClient MONGO_CLIENT;

    public static Future<Void> init(JsonObject config)
    {
        var connectionString = config.getString("connection_string", "mongodb://localhost:27017");

        var dbName = config.getString("db_name", Constants.DB_NAME);

        var minPoolSize = config.getInteger("minMongodbPoolSize", 3);

        var maxPoolSize = config.getInteger("maxMongodbPoolSize", 8);

        System.out.println("Connecting to Mongodb...");

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
                    System.err.println("Failed to connect: " + response.cause().getMessage());

                    promise.fail(response.cause());
                }
            });
        }

        catch (Exception exception)
        {
            System.err.println("Failed to initialize mongo client. " + exception);
        }

        return promise.future();
    }

    public static io.vertx.ext.mongo.MongoClient getMongoClient()
    {
        return MONGO_CLIENT;
    }
}
