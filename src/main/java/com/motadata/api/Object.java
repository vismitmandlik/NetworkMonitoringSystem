package com.motadata.api;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class Object {
    public static void initRoutes(Router router)
    {
        router.post("/api/object/provision").handler(BodyHandler.create()).handler(com.motadata.services.ObjectManager::pollDevices);
    }
}
