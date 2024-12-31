package com.motadata.api;

import com.motadata.services.ObjectManager;
import io.vertx.ext.web.Router;

public class Object
{
    public static void init(Router router)
    {
        router.post("/provision").handler(ObjectManager::provisionDevices);
    }
}
