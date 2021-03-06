/*
 * Copyright (c) 2017 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.networknt.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.config.Config;
import com.networknt.rpc.router.JsonHandler;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.status.Status;
import com.networknt.utility.NioUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.ws.spi.http.HttpExchange;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * This is the interface that every business handler should extend from. It has two default methods
 * that can be shared by all handlers.
 *
 * @author Steve Hu
 */
public interface Handler {
    Logger logger = LoggerFactory.getLogger(Handler.class);

    String ERROR_NOT_DEFINED = "ERR10042";
    String STATUS_VALIDATION_ERROR = "ERR11004";

    ByteBuffer handle (HttpServerExchange exchange, Object object);

    default ByteBuffer validate(String serviceId, Object object) {
        // get schema from serviceId, remember that the schema is for the data object only.
        // the input object is the data attribute of the request body.
        Map<String, Object> serviceMap = (Map<String, Object>)JsonHandler.schema.get(serviceId);
        if(logger.isDebugEnabled()) {
            try {
                logger.debug("serviceId = " + serviceId  + " serviceMap = " + Config.getInstance().getMapper().writeValueAsString(serviceMap));
            } catch (Exception e) {
                logger.error("Exception:", e);
            }
        }
        JsonNode jsonNode = Config.getInstance().getMapper().valueToTree(serviceMap.get("schema"));
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance();
        JsonSchema schema = factory.getSchema(jsonNode);
        Set<ValidationMessage> errors = schema.validate(Config.getInstance().getMapper().valueToTree(object));
        ByteBuffer bf = null;
        if(errors.size() > 0) {
            try {
                Status status = new Status(STATUS_VALIDATION_ERROR, Config.getInstance().getMapper().writeValueAsString(errors));
                bf = NioUtils.toByteBuffer(status.toString());
            } catch (JsonProcessingException e) {
                logger.error("Exception:", e);
            }
        }
        return bf;
    }

    /**
     * Return a Status object so that the handler can get the HTTP response code to set exchange response.
     * @param exchange HttpServerExchange used to set the response code
     * @param code Error code defined in status.yml
     * @param args A number of arguments in the error description
     * @return status Status object
     */
    default String getStatus(HttpServerExchange exchange, String code, final Object... args) {
        Status status = new Status(code, args);
        if(status.getStatusCode() == 0) {
            // There is no entry in status.yml for this particular error code.
            status = new Status(ERROR_NOT_DEFINED, code);
        }
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        logger.error(status.toString() + " at " + elements[2].getClassName() + "." + elements[2].getMethodName() + "(" + elements[2].getFileName() + ":" + elements[2].getLineNumber() + ")");
        // set status code here so that the response has the right status code.
        exchange.setStatusCode(status.getStatusCode());
        return status.toString();
    }
}
