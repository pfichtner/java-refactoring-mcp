package com.example;

public class Document implements Printable {

    private final String content;

    public Document(String content) {
        this.content = content;
    }

    @Override
    public void print() {
        System.out.println(content);
    }

    @Override
    public String describe() {
        return "Document: " + content;
    }

    public String getContent() {
        return content;
    }
}
