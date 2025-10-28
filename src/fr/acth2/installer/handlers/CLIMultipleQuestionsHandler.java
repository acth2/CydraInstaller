package fr.acth2.installer.handlers;

import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CLIMultipleQuestionsHandler {
    private boolean multipleSelectionMode = false;
    private BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    public CLIMultipleQuestionsHandler() {
        startInputListener();
    }

    private void startInputListener() {
        Thread inputThread = new Thread(() -> {
            try {
                while (true) {
                    if (System.in.available() > 0) {
                        String input = readSingleKey();
                        if (input != null && multipleSelectionMode) {
                            inputQueue.put(input);
                            simulateEnterKey();
                        }
                    }
                    Thread.sleep(10);
                }
            } catch (Exception ignored) {}
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }

    private String readSingleKey() {
        try {
            int input = System.in.read();
            if (input == 27) {
                if (System.in.available() >= 2) {
                    int next1 = System.in.read();
                    int next2 = System.in.read();
                    if (next1 == 91) {
                        switch (next2) {
                            case 65: return "up";
                            case 66: return "down";
                            case 67: return "right";
                            case 68: return "left";
                        }
                    }
                }
                return "esc";
            } else if (input == 10 || input == 13) {
                return "enter";
            } else if (input == ' ') {
                return "space";
            } else if (input == 'c' || input == 'C') {
                return "confirm";
            } else if (input == 'q' || input == 'Q') {
                return "quit";
            }
        } catch (IOException ignored) {}
        return null;
    }

    private void simulateEnterKey() {
        System.out.println();
    }

    public String getNextInput() {
        try {
            return inputQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    public void setMultipleSelectionMode(boolean enabled) {
        this.multipleSelectionMode = enabled;
        if (enabled) {
            System.out.println("Quick selection mode enabled - any key selects and moves forward");
        }
    }
}