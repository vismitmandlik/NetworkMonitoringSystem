package com.motadata.api;

import com.motadata.services.ObjectManager;
import io.vertx.ext.web.Router;

public class Object
{
    public static void initRoutes(Router router)
    {
        router.post("/provision").handler(ObjectManager::provisionDevices);

        router.post("/poll").handler(ObjectManager::provisionDevices);
    }
}
