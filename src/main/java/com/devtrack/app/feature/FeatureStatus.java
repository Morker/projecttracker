package com.devtrack.app.feature;

public enum FeatureStatus {
    PLANNED("Planned"),
    IN_PROGRESS("In Progress"),
    DONE("Done");

    private final String label;

    FeatureStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
