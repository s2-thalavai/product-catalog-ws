package com.infy.catalog.ws.handler;

import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server-side SOAP handler (the JAX-WS equivalent of a servlet filter):
 * <ul>
 *   <li>takes the caller's X-Correlation-Id (or generates one) and puts it in the logging MDC,</li>
 *   <li>echoes it in the HTTP response header and in every fault's traceId,</li>
 *   <li>logs one access line per call with operation, outcome and duration.</li>
 * </ul>
 * Payloads are never logged (they may contain business data).
 */
public class CorrelationIdHandler implements SOAPHandler<SOAPMessageContext> {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdHandler.class);
    private static final String START_NANOS = CorrelationIdHandler.class.getName() + ".startNanos";
    /** Accept only short, safe ids so callers cannot inject text into our logs. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Set<QName> getHeaders() {
        return Collections.emptySet();
    }

    @Override
    public boolean handleMessage(SOAPMessageContext context) {
        if (isOutbound(context)) {
            complete(context, "OK");
        } else {
            begin(context);
        }
        return true;
    }

    @Override
    public boolean handleFault(SOAPMessageContext context) {
        if (isOutbound(context)) {
            complete(context, "FAULT");
        }
        return true;
    }

    @Override
    public void close(MessageContext context) {
        MDC.remove(MDC_KEY);
    }

    private static void begin(MessageContext context) {
        String traceId = incomingId(context).orElseGet(() -> UUID.randomUUID().toString());
        MDC.put(MDC_KEY, traceId);
        context.put(START_NANOS, System.nanoTime());
    }

    private static void complete(MessageContext context, String outcome) {
        String traceId = MDC.get(MDC_KEY);
        if (traceId != null) {
            addResponseHeader(context, traceId);
        }
        long durationMs = context.get(START_NANOS) instanceof Long start
                ? (System.nanoTime() - start) / 1_000_000
                : -1;
        String operation = context.get(MessageContext.WSDL_OPERATION) instanceof QName qName
                ? qName.getLocalPart()
                : "unknown";
        log.info("soap operation={} outcome={} durationMs={}", operation, outcome, durationMs);
    }

    static Optional<String> incomingId(MessageContext context) {
        if (!(context.get(MessageContext.HTTP_REQUEST_HEADERS) instanceof Map<?, ?> headers)) {
            return Optional.empty();
        }
        for (Map.Entry<?, ?> header : headers.entrySet()) {
            if (header.getKey() instanceof String name && name.equalsIgnoreCase(HEADER)
                    && header.getValue() instanceof List<?> values && !values.isEmpty()) {
                String value = String.valueOf(values.get(0)).trim();
                if (SAFE_ID.matcher(value).matches()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private static void addResponseHeader(MessageContext context, String traceId) {
        Map<String, List<String>> headers = new HashMap<>();
        if (context.get(MessageContext.HTTP_RESPONSE_HEADERS) instanceof Map<?, ?> existing) {
            existing.forEach((key, value) -> {
                if (key instanceof String name && value instanceof List<?> list) {
                    headers.put(name, list.stream().map(String::valueOf).toList());
                }
            });
        }
        headers.put(HEADER, List.of(traceId));
        context.put(MessageContext.HTTP_RESPONSE_HEADERS, headers);
    }

    private static boolean isOutbound(MessageContext context) {
        return Boolean.TRUE.equals(context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY));
    }
}
