package com.motadata.api;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class Discovery
{
    public static void initRoutes(Router router)
    {
        System.out.println(Thread.currentThread().getName());
        router.post("/api/discovery").handler(BodyHandler.create()).handler(com.motadata.services.Discovery::discovery);
    }
}
