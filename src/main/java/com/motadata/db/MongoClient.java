package com.motadata.db;

import com.motadata.Main;
import com.motadata.constants.Constants;
import io.vertx.core.json.JsonObject;

public class MongoClient
{
    private static io.vertx.ext.mongo.MongoClient MONGO_CLIENT;

    public static void init(JsonObject config)
    {
        var connectionString = config.getString("connection_string", "mongodb://localhost:27017");

        var dbName = config.getString("db_name", Constants.DB_NAME);

        MONGO_CLIENT = io.vertx.ext.mongo.MongoClient.createShared(Main.vertx(), new JsonObject().put("connection_string", connectionString).put("db_name", dbName));

        MONGO_CLIENT.runCommand("ping", new JsonObject().put("ping", 1), response ->
        {
            if (response.succeeded())
            {
                System.out.println("Connected to MongoDB  " );
            }
            else
            {
                System.err.println("Failed to connect: " + response.cause().getMessage());
            }
        });
    }

    public static io.vertx.ext.mongo.MongoClient getMongoClient()
    {
        return MONGO_CLIENT;
    }
}
