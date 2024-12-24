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

        System.out.println("Connecting to Mongodb...");

        /*Set minPoolSize = No. of Verticles and maxPoolSize = 100 {default} */
        MONGO_CLIENT = io.vertx.ext.mongo.MongoClient.createShared(Main.vertx(), new JsonObject().put("connection_string", connectionString).put("db_name", dbName).put("minPoolSize", 3));

        // Create a promise to track the success or failure of the connection
        Promise<Void> promise = Promise.promise();

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

        return promise.future();
    }

    public static io.vertx.ext.mongo.MongoClient getMongoClient()
    {
        return MONGO_CLIENT;
    }
}
