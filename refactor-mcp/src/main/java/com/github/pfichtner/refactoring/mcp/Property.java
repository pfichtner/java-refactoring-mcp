package com.github.pfichtner.refactoring.mcp;

/** Canonical MCP schema property descriptors. Use {@link Options} to compose them into tool schemas. */
enum Property {

    // -------------------------------------------------------------------------
    // Position locators
    // -------------------------------------------------------------------------
    FILE("file", String.class, "Absolute path to the source file."),
    PROJECT_ROOT("project_root", String.class, "Absolute project root (Maven or Gradle)."),
    LINE("line", Integer.class, "1-based line. Use with 'column' OR a name-based locator."),
    COLUMN("column", Integer.class, "1-based column. Use with 'line'."),
    START_LINE("start_line", Integer.class, "1-based start line of the selection."),
    START_COLUMN("start_column", Integer.class, "1-based start column of the selection."),
    END_LINE("end_line", Integer.class, "1-based end line of the selection."),
    END_COLUMN("end_column", Integer.class, "1-based end column of the selection (exclusive)."),

    // -------------------------------------------------------------------------
    // Name-based locators
    // -------------------------------------------------------------------------
    METHOD("method", String.class, "Name-based locator: method name, e.g. \"speak\" or \"speak(int)\" for overloads."),
    FIELD("field", String.class, "Name-based locator: field name, e.g. \"amount\"."),
    TYPE("type", String.class, "Name-based locator: type (class/interface/enum), e.g. \"OrderService\"."),
    VARIABLE("variable", String.class, "Name-based locator: local variable name, e.g. \"result\"."),
    PARAMETER("parameter", String.class, "Name-based locator: parameter name. Requires 'method'."),
    CLASS("class", String.class, "Optional: scope to a specific class when the file contains multiple types."),

    // -------------------------------------------------------------------------
    // Boolean flags
    // -------------------------------------------------------------------------
    REPLACE_ALL("replace_all", Boolean.class, "Replace all identical occurrences."),
    REMOVE_DECLARATION("remove_declaration", Boolean.class, "Also remove the declaration."),
    CASCADE("cascade", Boolean.class, "Also remove overriding/implementing methods in subclasses (default true)."),
    AS_RECORD("as_record", Boolean.class, "Generate a record instead of a plain class (Java 16+)."),
    GENERATE_SETTER("generate_setter", Boolean.class, "Also generate a setter and rewrite write access sites."),
    MAKE_CONSTRUCTOR_PRIVATE("make_constructor_private", Boolean.class, "Change the constructor visibility to private."),
    WIDEN_VISIBILITY("widen_visibility", Boolean.class, "Widen the moved element's visibility to the minimum required for correctness (e.g. private → protected for pull-up, package-private or public for cross-package moves). Defaults to true."),

    // -------------------------------------------------------------------------
    // Name arguments
    // -------------------------------------------------------------------------
    NEW_NAME("new_name", String.class, "New name for the symbol."),
    METHOD_NAME("method_name", String.class, "Name for the new method."),
    FACTORY_METHOD_NAME("factory_method_name", String.class, "Simple name for the new factory method, e.g. 'of' or 'create'."),
    VAR_NAME("var_name", String.class, "Name for the introduced variable."),
    CONST_NAME("const_name", String.class, "Name for the constant (conventionally UPPER_CASE)."),
    PARAM_NAME("param_name", String.class, "Name for the new parameter."),
    PARAM_TYPE("param_type", String.class, "Explicit type for the parameter (inferred if omitted)."),
    CLASS_NAME("class_name", String.class, "Simple name for the new parameter-object class."),
    PARAM_OBJECT_NAME("param_object_name", String.class, "Name for the new parameter in the method (defaults to lower-camel of class_name)."),
    INTERFACE_NAME("interface_name", String.class, "Simple name for the new interface."),
    SUPERCLASS_NAME("superclass_name", String.class, "Simple name for the new abstract superclass."),
    NESTED_CLASS_NAME("nested_class_name", String.class, "Simple name for the new nested class."),
    INDIRECTION_METHOD_NAME("indirection_method_name", String.class, "Name for the new static wrapper method."),
    TARGET_CLASS("target_class", String.class, "Fully-qualified name of the target class, e.g. \"com.example.Report\"."),
    OLD_PACKAGE("old_package", String.class, "Fully-qualified source package, e.g. com.example.service."),
    NEW_PACKAGE("new_package", String.class, "Fully-qualified target package, e.g. com.example.util."),
    NEW_RETURN_TYPE("new_return_type", String.class, "New return type source text (e.g. \"double\"). Omit to leave unchanged."),
    REFACTORING("refactoring", String.class, "Refactoring type. Currently supported: \"rename\"."),

    // -------------------------------------------------------------------------
    // Array / list arguments
    // -------------------------------------------------------------------------
    METHOD_NAMES("method_names", String[].class, "Methods to include; empty = all public non-static."),
    PARAM_NAMES("param_names", String[].class, "Names of the contiguous parameters to group (>=2)."),
    PARAM_ORDER("param_order", Integer[].class, "New parameter order as 0-based indices, e.g. [1,0] swaps two params."),
    PARAM_TYPES("param_types", String[].class, "New types for parameters (parallel array; null or empty string leaves a parameter's type unchanged), e.g. [\"long\",null] changes the first param to long.");

    // -------------------------------------------------------------------------

    private final String key;
    private final Class<?> type;
    private final String description;

    private Property(String key, Class<?> type, String description) {
        this.key         = key;
        this.type        = type;
        this.description = description;
    }

    public String key() {
		return key;
	}
    
    public Class<?> type() {
		return type;
	}

    public String description() {
		return description;
	}

}