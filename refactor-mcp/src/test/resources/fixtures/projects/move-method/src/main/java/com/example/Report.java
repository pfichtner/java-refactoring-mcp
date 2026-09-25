package com.example;

public class Report {

    private final String title;

    public Report(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public String describe(Report report) {
        return "Length: " + report.title().length();
    }
}
