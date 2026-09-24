package com.github.pfichtner.refactoring.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFileTest {

	@TempDir
	Path tmp;

	@Test
	void content_is_read_from_disk() throws Exception {
		Path file = tmp.resolve("A.java");
		Files.writeString(file, "class A {}");

		SourceFile source = new SourceFile(file);

		assertThat(source.content()).isEqualTo("class A {}");
	}

	@Test
	void content_is_read_once_and_cached() throws Exception {
		Path file = tmp.resolve("B.java");
		Files.writeString(file, "class B {}");

		SourceFile source = new SourceFile(file);
		String first = source.content();

		Files.writeString(file, "class B { /* changed */ }");
		String second = source.content();

		assertThat(second).isSameAs(first);
		assertThat(second).isEqualTo("class B {}");
	}

	@Test
	void path_is_available_without_reading_content() {
		Path file = tmp.resolve("C.java");

		assertThat(new SourceFile(file).path()).isEqualTo(file);
	}

}