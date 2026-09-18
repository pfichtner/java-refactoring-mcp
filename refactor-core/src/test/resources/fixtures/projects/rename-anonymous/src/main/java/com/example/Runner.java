package com.example;

public class Runner {
    interface Task {
        void execute();
    }

    public void run() {
        Task t = new Task() {
            @Override
            public void execute() {
                System.out.println("running");
            }
        };
        t.execute();
    }
}
