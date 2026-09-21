package com.github.pfichtner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;

class PushDownFieldTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void push_down_copies_field_to_all_subclasses() throws Exception {
        Path projectRoot  = fixtures.projectPath("projects/push-down-field");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot      = project.sourceRoots().get(0);
        Path vehicleFile  = srcRoot.resolve("com/example/Vehicle.java");
        Path carFile      = srcRoot.resolve("com/example/Car.java");
        Path truckFile    = srcRoot.resolve("com/example/Truck.java");

        String vehicleSrc = Files.readString(vehicleFile);
        String carSrc     = Files.readString(carFile);
        String truckSrc   = Files.readString(truckFile);

        int offset = Fixtures.offsetOf(vehicleSrc, "maxSpeed");
        Map<Path, String> changed = JdtPushDownField.pushDown(project, vehicleFile, offset);

        assertThat(changed.size()).isEqualTo(3);

        Path absVehicle = vehicleFile.toAbsolutePath().normalize();
        Path absCar     = carFile.toAbsolutePath().normalize();
        Path absTruck   = truckFile.toAbsolutePath().normalize();

        Approvals.verify(
            RefactoringStoryBoard.titled("Push down field: Vehicle.maxSpeed → Car, Truck")
                .inputProject(Map.of(
                    "Car.java",     carSrc,
                    "Truck.java",   truckSrc,
                    "Vehicle.java", vehicleSrc))
                .refactoring("push down field",
                    "`Vehicle.maxSpeed` → subclasses",
                    Fixtures.lineCol(vehicleSrc, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void reject_no_subclasses_in_project() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/push-down-field");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot   = project.sourceRoots().get(0);
        // Car has no subclasses in the fixture project
        Path carFile   = srcRoot.resolve("com/example/Car.java");
        String source  = Files.readString(carFile);
        int offset     = Fixtures.offsetOf(source, "doors");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtPushDownField.pushDown(project, carFile, offset)).actual();
        assertThat(ex.getMessage()).contains("No direct subclasses");

        Approvals.verify(
            RefactoringStoryBoard.titled("Push down field rejected: Car has no subclasses")
                .javaSection("Input: Car.java", source)
                .refactoring("push down field", "`Car.doors`", Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
