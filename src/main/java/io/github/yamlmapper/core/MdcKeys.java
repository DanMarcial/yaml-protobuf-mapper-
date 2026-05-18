package io.github.yamlmapper.core;

/**
 * Constants for MDC (Mapped Diagnostic Context) keys.
 *
 * <p>These keys are used with SLF4J MDC to provide context
 * information in log messages during mapping operations.
 *
 * <p>Example log output with MDC:
 * <pre>
 * 2024-03-15 10:30:00 [configId=search, targetType=UserEvent] DEBUG - Mapping JSON to UserEvent
 * </pre>
 */
public final class MdcKeys {

    /** MDC key for the configuration ID being used. */
    public static final String CONFIG_ID = "configId";

    /** MDC key for the target Protobuf message type. */
    public static final String TARGET_TYPE = "targetType";

    private MdcKeys() {
        // Utility class
    }
}
