package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Approval gallery for all CLI dry-run scenarios.
 *
 * Each test exercises one subcommand in preview mode. Assertions (exit code 0,
 * no files written) are absorbed into {@link CliTestBed#preview(String...)} so
 * the method body is just {@code Approvals.verify(bed.preview(...))}.
 */
@ExtendWith(CliTestBedExtension.class)
class CliRefactoringStoryBook {

    // -------------------------------------------------------------------------
    // Single-file tools
    // -------------------------------------------------------------------------

    @Test
    @CliFixture(root = "fixtures/extract/simple/input/Greeter.java")
    void extract_method(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "extract",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/extract-var/simple/input/Foo.java")
    void extract_variable(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "extract-var",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "21",
                "--name", "answer",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/extract-const/simple/input/Foo.java")
    void extract_constant(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "extract-const",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "23",
                "--name", "PI",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/inline-var/multiple-uses/input/Foo.java")
    void inline_variable(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "inline-var",
                "--file", bed.root().toString(),
                "--line", "3", "--column", "16",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/inline-constant/int-constant/input/Foo.java")
    void inline_constant(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "inline-const",
                "--file", bed.root().toString(),
                "--line", "5", "--column", "21",
                "--all-occurrences",
                "--remove-declaration",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/extract-superclass/simple/input/Animal.java")
    void extract_superclass(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "extract-superclass",
                "--file", bed.root().toString(),
                "--name", "BaseAnimal",
                "--superclass-file", bed.root().getParent().resolve("BaseAnimal.java").toString(),
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/extract-interface/simple/input/Calculator.java")
    void extract_interface(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "extract-interface",
                "--file", bed.root().toString(),
                "--name", "Arithmetic",
                "--interface-file", bed.root().getParent().resolve("Arithmetic.java").toString(),
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/decompose-conditional/basic/input/Foo.java")
    void decompose_conditional(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "decompose-conditional",
                "--file", bed.root().toString(),
                "--line", "6", "--column", "9",
                "--name", "isAdultPremium",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/convert-anonymous/simple/input/Outer.java")
    void convert_anonymous_to_nested(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "convert-anonymous",
                "--file", bed.root().toString(),
                "--line", "4", "--column", "22",
                "--name", "Worker",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/convert-nested/static-class/input/Outer.java")
    void convert_nested_to_top_level(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "convert-nested",
                "--file", bed.root().toString(),
                "--line", "15", "--column", "25",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/promote-to-field/without-initializer/input/Counter.java")
    void promote_to_field(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "promote-to-field",
                "--file", bed.root().toString(),
                "--line", "4", "--column", "15",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/introduce-indirection/static-method/input/MathUtils.java")
    void introduce_indirection(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "introduce-indirection",
                "--file", bed.root().toString(),
                "--line", "3", "--column", "26",
                "--name", "computeSquare",
                "--dry-run"));
    }

    // -------------------------------------------------------------------------
    // Project tools
    // -------------------------------------------------------------------------

    @Test
    @CliFixture(root = "fixtures/projects/rename-method/pom.xml")
    void rename_method(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");
        Approvals.verify(bed.preview(
                "rename",
                "--file", calcFile.toString(),
                "--line", "4", "--column", "16",
                "--name", "plus",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/rename-method/pom.xml")
    void rename_by_method_name(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");
        Approvals.verify(bed.preview(
                "rename",
                "--file", calcFile.toString(),
                "--method", "add",
                "--name", "plus",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/rename-method/pom.xml")
    void inline_method(CliTestBed bed) throws Exception {
        Path appFile = bed.root().resolve("src/main/java/com/example/App.java");
        Approvals.verify(bed.preview(
                "inline-method",
                "--file", appFile.toString(),
                "--line", "6", "--column", "27",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/pull-up-method/pom.xml")
    void pull_up_method(CliTestBed bed) throws Exception {
        Path dogFile = bed.root().resolve("src/main/java/com/example/Dog.java");
        Approvals.verify(bed.preview(
                "pull-up",
                "--file", dogFile.toString(),
                "--method", "speak",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/push-down-method/pom.xml")
    void push_down_method(CliTestBed bed) throws Exception {
        Path shapeFile = bed.root().resolve("src/main/java/com/example/Shape.java");
        Approvals.verify(bed.preview(
                "push-down",
                "--file", shapeFile.toString(),
                "--method", "area",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/move-method/pom.xml")
    void move_method(CliTestBed bed) throws Exception {
        Path printerFile = bed.root().resolve("src/main/java/com/example/Printer.java");
        Approvals.verify(bed.preview(
                "move-method",
                "--file", printerFile.toString(),
                "--method", "format",
                "--target-class", "com.example.Report",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/remove-method/pom.xml")
    void remove_method(CliTestBed bed) throws Exception {
        Path printableFile = bed.root().resolve("src/main/java/com/example/Printable.java");
        Approvals.verify(bed.preview(
                "remove-method",
                "--file", printableFile.toString(),
                "--method", "print",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/pull-up-field/pom.xml")
    void pull_up_field(CliTestBed bed) throws Exception {
        Path dogFile = bed.root().resolve("src/main/java/com/example/Dog.java");
        Approvals.verify(bed.preview(
                "pull-up-field",
                "--file", dogFile.toString(),
                "--field", "breed",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/push-down-field/pom.xml")
    void push_down_field(CliTestBed bed) throws Exception {
        Path vehicleFile = bed.root().resolve("src/main/java/com/example/Vehicle.java");
        Approvals.verify(bed.preview(
                "push-down-field",
                "--file", vehicleFile.toString(),
                "--field", "maxSpeed",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/remove-param/pom.xml")
    void remove_param(CliTestBed bed) throws Exception {
        Path computationFile = bed.root().resolve("src/main/java/com/example/Computation.java");
        Approvals.verify(bed.preview(
                "remove-param",
                "--file", computationFile.toString(),
                "--method", "add",
                "--parameter", "c",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/introduce-param/pom.xml")
    void introduce_param(CliTestBed bed) throws Exception {
        Path greeterFile = bed.root().resolve("src/main/java/com/example/Greeter.java");
        Approvals.verify(bed.preview(
                "introduce-param",
                "--file", greeterFile.toString(),
                "--start-line", "5", "--start-column", "40",
                "--end-line", "5", "--end-column", "47",
                "--name", "whom",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/introduce-param-object/pom.xml")
    void introduce_param_object(CliTestBed bed) throws Exception {
        Path printerFile = bed.root().resolve("src/main/java/com/example/Printer.java");
        Approvals.verify(bed.preview(
                "introduce-param-object",
                "--file", printerFile.toString(),
                "--line", "4",
                "--column", "17",
                "--params", "x,y",
                "--class-name", "Coordinate",
                "--record",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/static-factory/pom.xml")
    void introduce_static_factory(CliTestBed bed) throws Exception {
        Path counterFile = bed.root().resolve("src/main/java/com/example/Counter.java");
        Approvals.verify(bed.preview(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/static-factory/pom.xml")
    void introduce_static_factory_with_private_constructor(CliTestBed bed) throws Exception {
        Path counterFile = bed.root().resolve("src/main/java/com/example/Counter.java");
        Approvals.verify(bed.preview(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of",
                "--private-constructor",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/encapsulate-field/pom.xml")
    void encapsulate_field(CliTestBed bed) throws Exception {
        Path personFile = bed.root().resolve("src/main/java/com/example/Person.java");
        Approvals.verify(bed.preview(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/encapsulate-field/pom.xml")
    void encapsulate_field_with_setter(CliTestBed bed) throws Exception {
        Path personFile = bed.root().resolve("src/main/java/com/example/Person.java");
        Approvals.verify(bed.preview(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name",
                "--setter",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/convert-to-record/pom.xml")
    void convert_to_record(CliTestBed bed) throws Exception {
        Path pointFile = bed.root().resolve("src/main/java/com/example/Point.java");
        Approvals.verify(bed.preview(
                "convert-to-record",
                "--file", pointFile.toString(),
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/change-method-signature/pom.xml")
    void change_method_signature(CliTestBed bed) throws Exception {
        Path converterFile = bed.root().resolve("src/main/java/com/example/Converter.java");
        Approvals.verify(bed.preview(
                "change-method-signature",
                "--file", converterFile.toString(),
                "--method", "convert",
                "--param-order", "1,0",
                "--return-type", "Object",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/move-static/pom.xml")
    void move_static_member(CliTestBed bed) throws Exception {
        Path mathUtilsFile = bed.root().resolve("src/main/java/com/example/MathUtils.java");
        Approvals.verify(bed.preview(
                "move-static",
                "--file", mathUtilsFile.toString(),
                "--line", "5", "--column", "5",
                "--target", "com.example.Helpers",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/rename-package/pom.xml")
    void rename_package(CliTestBed bed) throws Exception {
        Approvals.verify(bed.preview(
                "rename-package",
                "--project", bed.root().toString(),
                "--old-package", "com.example.service",
                "--new-package", "com.example.util",
                "--dry-run"));
    }

    @Test
    @CliFixture(root = "fixtures/projects/move-class/pom.xml")
    void move_class(CliTestBed bed) throws Exception {
        Path calculatorFile = bed.root().resolve("src/main/java/com/example/service/Calculator.java");
        Approvals.verify(bed.preview(
                "move-class",
                "--file", calculatorFile.toString(),
                "--package", "com.example.util",
                "--dry-run"));
    }
}
