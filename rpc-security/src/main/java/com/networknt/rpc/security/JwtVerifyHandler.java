package com.networknt.rpc.security;

import com.networknt.audit.AuditHandler;
import com.networknt.config.Config;
import com.networknt.exception.ExpiredTokenException;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.security.JwtHelper;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a middleware handler that handles security verification for light-hybrid-4j framework. It
 * verifies token signature and token expiration. Unlike the similar handler in light-rest-4j handles
 * scope verification, this one doesn't as it is placed before rpc-router and exact service is unknown.
 *
 * The scope verification will be done in the rpc-router once the service is identified and schema is
 * found. Both handlers share the same security.yml but just focus on different properties.
 *
 * @author Steve Hu
 */
public class JwtVerifyHandler implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(JwtVerifyHandler.class);

    static final String STATUS_INVALID_AUTH_TOKEN = "ERR10000";
    static final String STATUS_AUTH_TOKEN_EXPIRED = "ERR10001";
    static final String STATUS_MISSING_AUTH_TOKEN = "ERR10002";

    static final Map<String, Object> config = Config.getInstance().getJsonMapConfig(JwtHelper.SECURITY_CONFIG);

    private volatile HttpHandler next;

    public JwtVerifyHandler() {}

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        HeaderMap headerMap = exchange.getRequestHeaders();
        String authorization = headerMap.getFirst(Headers.AUTHORIZATION);
        String jwt = JwtHelper.getJwtFromAuthorization(authorization);
        if(jwt != null) {
            try {
                JwtClaims claims = JwtHelper.verifyJwt(jwt);
                Map<String, Object> auditInfo = new HashMap<>();
                auditInfo.put(Constants.ENDPOINT, exchange.getRequestURI());
                auditInfo.put(Constants.CLIENT_ID, claims.getStringClaimValue(Constants.CLIENT_ID));
                auditInfo.put(Constants.USER_ID, claims.getStringClaimValue(Constants.USER_ID));
                // This is the id-token scope, it is put into the header for audit and rpc-router for token verification
                // Need to remove the space in order for rpc-router to parse and verify scope
                auditInfo.put(Constants.SCOPE, claims.getStringListClaimValue(Constants.SCOPE).toString().replaceAll("\\s+",""));
                exchange.putAttachment(AuditHandler.AUDIT_INFO, auditInfo);
                next.handleRequest(exchange);
            } catch (InvalidJwtException e) {
                // only log it and unauthorized is returned.
                logger.error("Exception: ", e);
                Status status = new Status(STATUS_INVALID_AUTH_TOKEN);
                exchange.setStatusCode(status.getStatusCode());
                exchange.getResponseSender().send(status.toString());
            } catch (ExpiredTokenException e) {
                Status status = new Status(STATUS_AUTH_TOKEN_EXPIRED);
                exchange.setStatusCode(status.getStatusCode());
                exchange.getResponseSender().send(status.toString());
            }
        } else {
            Status status = new Status(STATUS_MISSING_AUTH_TOKEN);
            exchange.setStatusCode(status.getStatusCode());
            exchange.getResponseSender().send(status.toString());
        }
    }

    @Override
    public HttpHandler getNext() {
        return next;
    }

    @Override
    public MiddlewareHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    @Override
    public boolean isEnabled() {
        Object object = config.get(JwtHelper.ENABLE_VERIFY_JWT);
        return object != null && (Boolean) object;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(JwtVerifyHandler.class.getName(), config, null);
    }

}