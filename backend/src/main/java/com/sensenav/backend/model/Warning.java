package com.sensenav.backend.model;

public class Warning {

    private Long id;
    private String title;
    private String location;
    private String description;
    private String dataSource;
    private String suggestedAction;

    public Warning(
            Long id,
            String title,
            String location,
            String description,
            String dataSource,
            String suggestedAction
    ) {
        this.id = id;
        this.title = title;
        this.location = location;
        this.description = description;
        this.dataSource = dataSource;
        this.suggestedAction = suggestedAction;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public String getDataSource() {
        return dataSource;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }
}
