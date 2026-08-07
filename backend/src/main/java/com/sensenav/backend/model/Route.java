package com.sensenav.backend.model;

public class Route {

    private Long id;
    private String routeName;
    private String startLocation;
    private String destination;
    private double rating;
    private String sensoryRisk;
    private int duration;
    private String roadName;
    private boolean recommended;

    public Route(
            Long id,
            String routeName,
            String startLocation,
            String destination,
            double rating,
            String sensoryRisk,
            int duration,
            String roadName,
            boolean recommended
    ) {
        this.id = id;
        this.routeName = routeName;
        this.startLocation = startLocation;
        this.destination = destination;
        this.rating = rating;
        this.sensoryRisk = sensoryRisk;
        this.duration = duration;
        this.roadName = roadName;
        this.recommended = recommended;
    }

    public Long getId() {
        return id;
    }

    public String getRouteName() {
        return routeName;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public String getDestination() {
        return destination;
    }

    public double getRating() {
        return rating;
    }

    public String getSensoryRisk() {
        return sensoryRisk;
    }

    public int getDuration() {
        return duration;
    }

    public String getRoadName() {
        return roadName;
    }

    public boolean isRecommended() {
        return recommended;
    }
}