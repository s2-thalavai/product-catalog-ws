package com.infy.catalog.infra;

import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Process-wide settings that must be applied before Metro classes load.
 */
public final class SoapRuntimeDefaults {

    private SoapRuntimeDefaults() {
    }

    public static void configure() {
        // Never put server stack traces into SOAP faults (property name differs across Metro versions).
        System.setProperty("com.sun.xml.ws.fault.SOAPFaultBuilder.captureStackTrace", "false");
        System.setProperty("com.sun.xml.ws.fault.SOAPFaultBuilder.disableCaptureStackTrace", "true");

        // Metro logs through java.util.logging; route it to SLF4J/Logback so all logs share one format.
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
    }
}
