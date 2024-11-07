package com.motadata;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import com.motadata.api.Server;
public class Main extends AbstractVerticle
{
    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        DeploymentOptions optionsForMyVerticle = new DeploymentOptions().setInstances(10).setWorkerPoolSize(2);

        vertx.deployVerticle(Server.class.getName(), optionsForMyVerticle, res ->
        {
            if (res.succeeded())
            {
                System.out.println("Successfully deployed 10 instances of MyVerticle");
            }
            else
            {
                System.out.println("Failed to deploy MyVerticle: " + res.cause());
            }
        });

    }
}
