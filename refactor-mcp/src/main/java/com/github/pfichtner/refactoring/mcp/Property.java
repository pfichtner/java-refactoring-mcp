package com.github.pfichtner.refactoring.mcp;

import java.util.Map;

/** Canonical MCP schema property descriptors. Use {@link Options} to compose them into tool schemas. */
enum Property {

    // -------------------------------------------------------------------------
    // Position locators
    // -------------------------------------------------------------------------
    FILE("file",                 str("Absolute path to the source file.")),
    PROJECT_ROOT("project_root", str("Absolute project root (Maven or Gradle).")),
    LINE("line",                 integer("1-based line. Use with 'column' OR a name-based locator.")),
    COLUMN("column",             integer("1-based column. Use with 'line'.")),
    START_LINE("start_line",     integer("1-based start line of the selection.")),
    START_COLUMN("start_column", integer("1-based start column of the selection.")),
    END_LINE("end_line",         integer("1-based end line of the selection.")),
    END_COLUMN("end_column",     integer("1-based end column of the selection (exclusive).")),

    // -------------------------------------------------------------------------
    // Name-based locators
    // -------------------------------------------------------------------------
    METHOD("method",       str("Name-based locator: method name, e.g. \"speak\" or \"speak(int)\" for overloads.")),
    FIELD("field",         str("Name-based locator: field name, e.g. \"amount\".")),
    TYPE("type",           str("Name-based locator: type (class/interface/enum), e.g. \"OrderService\".")),
    VARIABLE("variable",   str("Name-based locator: local variable name, e.g. \"result\".")),
    PARAMETER("parameter", str("Name-based locator: parameter name. Requires 'method'.")),
    CLASS("class",         str("Optional: scope to a specific class when the file contains multiple types.")),

    // -------------------------------------------------------------------------
    // Boolean flags
    // -------------------------------------------------------------------------
    REPLACE_ALL("replace_all",                   bool("Replace all identical occurrences.")),
    ALL_OCCURRENCES("all_occurrences",           bool("Replace all references in the file.")),
    REMOVE_DECLARATION("remove_declaration",     bool("Also remove the declaration.")),
    CASCADE("cascade",                           bool("Also remove overriding/implementing methods in subclasses (default true).")),
    AS_RECORD("as_record",                       bool("Generate a record instead of a plain class (Java 16+).")),
    GENERATE_SETTER("generate_setter",           bool("Also generate a setter and rewrite write access sites.")),
    MAKE_CONSTRUCTOR_PRIVATE("make_constructor_private", bool("Change the constructor visibility to private.")),

    // -------------------------------------------------------------------------
    // Name arguments
    // -------------------------------------------------------------------------
    NEW_NAME("new_name",                           str("New name for the symbol.")),
    METHOD_NAME("method_name",                     str("Name for the new method.")),
    FACTORY_METHOD_NAME("factory_method_name",     str("Simple name for the new factory method, e.g. 'of' or 'create'.")),
    VAR_NAME("var_name",                           str("Name for the introduced variable.")),
    CONST_NAME("const_name",                       str("Name for the constant (conventionally UPPER_CASE).")),
    PARAM_NAME("param_name",                       str("Name for the new parameter.")),
    PARAM_TYPE("param_type",                       str("Explicit type for the parameter (inferred if omitted).")),
    CLASS_NAME("class_name",                       str("Simple name for the new parameter-object class.")),
    PARAM_OBJECT_NAME("param_object_name",         str("Name for the new parameter in the method (defaults to lower-camel of class_name).")),
    INTERFACE_NAME("interface_name",               str("Simple name for the new interface.")),
    SUPERCLASS_NAME("superclass_name",             str("Simple name for the new abstract superclass.")),
    NESTED_CLASS_NAME("nested_class_name",         str("Simple name for the new nested class.")),
    INDIRECTION_METHOD_NAME("indirection_method_name", str("Name for the new static wrapper method.")),
    TARGET_CLASS("target_class", str("Fully-qualified name of the target class, e.g. \"com.example.Report\".")),
    OLD_PACKAGE("old_package",   str("Fully-qualified source package, e.g. com.example.service.")),
    NEW_PACKAGE("new_package",   str("Fully-qualified target package, e.g. com.example.util.")),
    NEW_RETURN_TYPE("new_return_type", str("New return type source text (e.g. \"double\"). Omit to leave unchanged.")),
    REFACTORING("refactoring",   str("Refactoring type. Currently supported: \"rename\".")),

    // -------------------------------------------------------------------------
    // Array / list arguments
    // -------------------------------------------------------------------------
    METHOD_NAMES("method_names", array("string",  "Methods to include; empty = all public non-static.")),
    PARAM_NAMES("param_names",   array("string",  "Names of the contiguous parameters to group (>=2).")),
    PARAM_ORDER("param_order",   array("integer", "New parameter order as 0-based indices, e.g. [1,0] swaps two params."));

    // -------------------------------------------------------------------------

    final String key;
    final Map<String, Object> descriptor;

    Property(String key, Map<String, Object> descriptor) {
        this.key        = key;
        this.descriptor = descriptor;
    }

        // -------------------------------------------------------------------------
    // Descriptor factories
    // -------------------------------------------------------------------------

    private static Map<String, Object> str(String desc) {
        return Map.of("type", "string", "description", desc);
    }

    private static Map<String, Object> integer(String desc) {
        return Map.of("type", "integer", "description", desc);
    }

    private static Map<String, Object> bool(String desc) {
        return Map.of("type", "boolean", "description", desc);
    }

    private static Map<String, Object> array(String itemType, String desc) {
        return Map.of("type", "array", "items", Map.of("type", itemType), "description", desc);
    }
}
