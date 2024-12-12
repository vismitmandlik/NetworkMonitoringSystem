package com.motadata;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import com.motadata.api.Server;
import com.motadata.db.Initializer;

public class Main
{
    static Vertx vertx = Vertx.vertx();

    static public Vertx getVertxInstance(){
        return vertx;
    }
    public static void main(String[] args)
    {
        Initializer initializer = new Initializer();

        initializer.initMongoClient();

        DeploymentOptions options = new DeploymentOptions();

        vertx.deployVerticle(Server.class.getName(), options, res ->
        {
            if (res.succeeded())
            {
                System.out.println("Successfully deployed Server Verticle");
            }
            else
            {
                System.out.println("Failed to deploy MyVerticle: " + res.cause());
            }
        });
    }
}
