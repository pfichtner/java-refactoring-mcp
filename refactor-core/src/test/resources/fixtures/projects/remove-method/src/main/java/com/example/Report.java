package com.example;

public class Report extends Document {

    private final String title;

    public Report(String title, String content) {
        super(content);
        this.title = title;
    }

    @Override
    public void print() {
        System.out.println(title + ": " + getContent());
    }

    public String getTitle() {
        return title;
    }
}
