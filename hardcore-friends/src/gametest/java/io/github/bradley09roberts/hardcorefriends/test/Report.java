package io.github.bradley09roberts.hardcorefriends.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Collects named checks from an in-game test and writes them to {@code test-reports/<name>.md}. */
public final class Report {
	private final String name;
	private final List<String> lines = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	public Report(String name) {
		this.name = name;
	}

	public boolean check(String description, boolean passed) {
		lines.add((passed ? "- PASS: " : "- FAIL: ") + description);
		if (!passed) {
			failures.add(description);
		}
		System.out.println("[HardcoreFriendsTest] " + (passed ? "PASS " : "FAIL ") + description);
		return passed;
	}

	public void note(String text) {
		lines.add("- note: " + text);
		System.out.println("[HardcoreFriendsTest] note " + text);
	}

	public List<String> failures() {
		return failures;
	}

	public void write() {
		try {
			Path dir = Path.of(System.getProperty("user.dir")).resolve("test-reports");
			Files.createDirectories(dir);
			List<String> out = new ArrayList<>();
			out.add("# " + name);
			out.add("");
			out.addAll(lines);
			Files.write(dir.resolve(name + ".md"), out);
		} catch (IOException e) {
			System.out.println("[HardcoreFriendsTest] could not write report: " + e);
		}
	}

	public void assertAllPassed() {
		if (!failures.isEmpty()) {
			throw new AssertionError(name + " failed: " + String.join("; ", failures));
		}
	}
}
