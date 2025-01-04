package com.motadata.api;

import com.motadata.constants.Constants;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class Object
{
    public static void init(Router router)
    {
        router.post("/provision").handler(context -> context.vertx().eventBus().request(Constants.PROVISION, context.body().asJsonObject(), reply ->
        {
            if (reply.succeeded())
            {
                context.response().setStatusCode(Constants.SC_200).end((reply.result().body().toString()));
            }
            else
            {
                context.response().setStatusCode(Constants.SC_500).end(new JsonObject().put(Constants.ERROR, "Failed to start provision").put(Constants.MESSAGE, reply.result()).encodePrettily());
            }
        }));

        router.post("/get").handler(context -> context.vertx().eventBus().request(Constants.OBJECT_POLLING_DATA, context.body().asJsonObject(), reply ->
        {
            if (reply.succeeded())
            {
                context.response().setStatusCode(Constants.SC_200).end((reply.result().body().toString()));
            }
            else
            {
                context.response().setStatusCode(Constants.SC_500).end(new JsonObject().put(Constants.ERROR, "Failed to start provision").put(Constants.MESSAGE, reply.result()).encodePrettily());
            }
        }));

        router.delete("/:objectId").handler(context -> {
            String objectId = context.request().getParam("objectId");
            if (objectId == null || objectId.isEmpty()) {
                context.response().setStatusCode(Constants.SC_400).end("objectId is required.");
                return;
            }

            context.vertx().eventBus().request(Constants.OBJECT_DELETE, new JsonObject().put(Constants.OBJECT_ID, objectId), reply -> {
                if (reply.succeeded()) {
                    context.response().setStatusCode(Constants.SC_200).end("Object deleted successfully.");
                } else {
                    context.response().setStatusCode(Constants.SC_500).end("Failed to delete object: " + reply.cause().getMessage());
                }
            });
        });
    }
}
