package com.motadata.api;

import com.motadata.services.ObjectManager;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class Object {

    public static void initRoutes(Router router)
    {

        router.post("/api/object/provision").handler(BodyHandler.create()).handler(ObjectManager::provisionDevices);

        router.post("/api/object/poll").handler(BodyHandler.create()).handler(ObjectManager::provisionDevices);

    }
}
