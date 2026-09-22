package com.github.pfichtner.refactoring.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects optional and required {@link Property} entries for an MCP tool schema.
 *
 * <pre>
 * Options opts = Options.builder()
 *         .add(METHOD_NAMES)            // optional
 *         .addRequired(FILE, INTERFACE_NAME)  // required
 *         .build();
 * Map.of("type", "object",
 *        "properties", opts.properties(),
 *        "required",   opts.required())
 * </pre>
 */
public final class Options {

    private final Map<String, Object> properties;
    private final List<String> required;

    private Options(Builder builder) {
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties));
        this.required   = List.copyOf(builder.required);
    }

    public Map<String, Object> properties() { return properties; }
    public List<String> required()          { return required; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final List<String>        required   = new ArrayList<>();

        /** Adds optional properties (appear in schema but not in required list). */
        public Builder add(Property... ps) {
            for (Property p : ps) properties.put(p.key, p.descriptor);
            return this;
        }

        /** Adds required properties (appear in both schema and required list). */
        public Builder addRequired(Property... ps) {
            for (Property p : ps) {
                properties.put(p.key, p.descriptor);
                required.add(p.key);
            }
            return this;
        }

        public Options build() { return new Options(this); }
    }
}
