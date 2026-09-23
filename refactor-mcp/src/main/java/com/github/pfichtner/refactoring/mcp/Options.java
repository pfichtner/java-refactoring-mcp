package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PARAMETER;
import static com.github.pfichtner.refactoring.mcp.Property.TYPE;
import static com.github.pfichtner.refactoring.mcp.Property.VARIABLE;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.pfichtner.refactoring.locator.Locator;

/**
 * Collects optional and required {@link Property} entries for an MCP tool schema,
 * and binds incoming request arguments to typed accessors via {@link #reader}.
 *
 * <pre>
 * Options opts = Options.builder()
 *         .add(METHOD_NAMES)                  // optional
 *         .addRequired(FILE, INTERFACE_NAME)  // required
 *         .build();
 *
 * // schema registration:
 * Tool.builder("my_tool", opts.toSchema())
 *
 * // inside the call handler:
 * var args = opts.reader(request.arguments());
 * String file          = args.getString(FILE);
 * String interfaceName = args.getString(INTERFACE_NAME);
 * List&lt;String&gt; methods = args.getStringList(METHOD_NAMES);  // empty list if absent
 * </pre>
 */
public final class Options {

    private final Map<String, Object> properties;
    private final List<String> required;

    private Options(Builder builder) {
        this.properties = new LinkedHashMap<>(builder.properties);
        this.required   = List.copyOf(builder.required);
    }

    /** Returns the JSON-Schema map {@code {"type":"object","properties":…,"required":…}}. */
    public Map<String, Object> toSchema() {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    /** Binds {@code args} (from {@code request.arguments()}) to typed accessors. */
    public Reader reader(Map<String, Object> args) { return new Reader(args); }

    /** Shorthand for an all-required schema (no optional properties). */
    public static Options of(Property... required) { return builder().addRequired(required).build(); }

    public static Builder builder() { return new Builder(); }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Reader — typed access to a bound request arguments map
    // -------------------------------------------------------------------------

    public static final class Reader {
        private final Map<String, Object> args;

        public Reader(Map<String, Object> args) { this.args = args; }

        /** Returns true if the property is present in the args (use for optional properties). */
        public boolean has(Property p) { return args.containsKey(p.key); }

        /** Returns the value as a String; null if absent (safe for optional String properties). */
        public String getString(Property p) { return (String) args.get(p.key); }

        /** Returns the value as a String, or {@code defaultValue} if absent. */
        public String getString(Property p, String defaultValue) {
            Object v = args.get(p.key);
            return v == null ? defaultValue : (String) v;
        }

        /** Returns the value as a {@link Path}; null if absent. */
        public Path getPath(Property p) {
            String v = getString(p);
            return v == null ? null : Path.of(v);
        }

        /** Returns the value as an int. Throws if absent. */
        public int getInt(Property p) { return ((Number) args.get(p.key)).intValue(); }

        /**
         * Returns {@code true} only if the value is Boolean.TRUE.
         * Safe for optional boolean flags that default to {@code false}.
         */
        public boolean getBoolean(Property p) { return Boolean.TRUE.equals(args.get(p.key)); }

        /**
         * Returns the boolean value, or {@code defaultValue} if absent.
         * Use for flags that default to {@code true} (e.g. cascade).
         */
        public boolean getBoolean(Property p, boolean defaultValue) {
            Object v = args.get(p.key);
            return v == null ? defaultValue : Boolean.TRUE.equals(v);
        }

        /**
         * Returns the value as a {@code List<String>}; empty list if absent.
         * Safe for optional list properties (e.g. method_names).
         */
        @SuppressWarnings("unchecked")
        public List<String> getStringList(Property p) {
            Object v = args.get(p.key);
            return v == null ? List.of() : (List<String>) v;
        }

        /**
         * Returns the value as an {@code int[]} by mapping each element via
         * {@link Number#intValue()}; null if absent.
         * Use for optional integer-array properties (e.g. param_order).
         */
        @SuppressWarnings("unchecked")
        public int[] getIntArray(Property p) {
            List<Number> list = (List<Number>) args.get(p.key);
            return list == null ? null : list.stream().mapToInt(Number::intValue).toArray();
        }

        /**
         * Returns the value as a {@code String[]}; null if absent.
         * Entries may themselves be null (e.g. param_types sparse override).
         * Use for optional string-array properties (e.g. param_types).
         */
        @SuppressWarnings("unchecked")
        public String[] getStringArray(Property p) {
            List<String> list = (List<String>) args.get(p.key);
            return list == null ? null : list.toArray(String[]::new);
        }

		public Locator getLocator() {
		    boolean hasLine      = has(LINE);
		    boolean hasCol       = has(COLUMN);
		    boolean hasMethod    = has(METHOD);
		    boolean hasField     = has(FIELD);
		    boolean hasType      = has(TYPE);
		    boolean hasParameter = has(PARAMETER);
		    boolean hasVariable  = has(VARIABLE);
		
		    boolean hasPosition = hasLine || hasCol;
		    boolean hasName     = hasMethod || hasField || hasType || hasParameter || hasVariable;
		
		    if (hasCol && !hasLine)
		        throw new IllegalArgumentException("'column' requires 'line' to also be specified.");
		
		    if (hasPosition && hasName) {
		        throw new IllegalArgumentException(
		                "Specify either a position (line / line+column) OR a name-based locator (method/field/type/variable/parameter), not both.");
		    }
		    if (!hasPosition && !hasName) {
		        throw new IllegalArgumentException(
		                "Specify either a position (line, or line+column) or a name-based locator (method, field, type, variable, or parameter).");
		    }
		
		    if (hasPosition) {
		        if (!hasCol)
		            return new Locator.LineOnly(getInt(LINE));
		        return new Locator.Position(getInt(LINE), getInt(COLUMN));
		    }
		
		    String className = getString(CLASS);
		
		    if (hasParameter) {
		        if (!hasMethod) {
		            throw new IllegalArgumentException("'parameter' requires 'method' to also be specified.");
		        }
		        return new Locator.ParameterInMethod(getString(METHOD), getString(PARAMETER));
		    }
		
		    if (hasVariable) {
		        if (!hasMethod)
		            throw new IllegalArgumentException(
		                    "'variable' requires 'method' to also be specified " +
		                    "(local variables can share names across methods).");
		        return new Locator.VariableName(getString(VARIABLE), getString(METHOD));
		    }
		
		    int kindCount = (hasMethod ? 1 : 0) + (hasField ? 1 : 0) + (hasType ? 1 : 0);
		    if (kindCount > 1) {
		        throw new IllegalArgumentException("Specify only one of: method, field, or type.");
		    }
		
		    if (hasMethod) return new Locator.MethodName(getString(METHOD), className);
		    if (hasField)  return new Locator.FieldName(getString(FIELD), className);
		    if (hasType)   return new Locator.TypeName(getString(TYPE));
		
		    throw new IllegalArgumentException("No valid locator found in arguments.");
		}
    }
    
}
