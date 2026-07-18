package com.devtrack.app.project;

public enum ProjectStatus {
    IDEA("Idea"),
    IN_PROGRESS("In Progress"),
    PAUSED("Paused"),
    DONE("Done"),
    ARCHIVED("Archived");

    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
