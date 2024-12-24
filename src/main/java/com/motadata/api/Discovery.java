package com.motadata.api;

import com.motadata.constants.Constants;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class Discovery
{
    public static void initRoutes(Router router)
    {
        router.post("/").handler(context -> context.vertx().eventBus().request(Constants.DISCOVERY_VERTICLE, context.body().asJsonObject(), reply ->
                {
                    if (reply.succeeded())
                    {
                        context.response().setStatusCode(200).end(((JsonObject) reply.result().body()).encodePrettily());
                    }
                    else
                    {
                        context.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to start discovery").encodePrettily());
                    }
                }));
    }
}
