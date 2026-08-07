package com.sensenav.backend.model;

public class Refuge {

    private Long id;
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private double rating;
    private String category;
    private String sensoryLevel;
    private String noiseLevel;
    private String crowdLevel;
    private String imageUrl;

    public Refuge(
            Long id,
            String name,
            String address,
            double latitude,
            double longitude,
            double rating,
            String category,
            String sensoryLevel,
            String noiseLevel,
            String crowdLevel,
            String imageUrl
    ) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rating = rating;
        this.category = category;
        this.sensoryLevel = sensoryLevel;
        this.noiseLevel = noiseLevel;
        this.crowdLevel = crowdLevel;
        this.imageUrl = imageUrl;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getRating() {
        return rating;
    }

    public String getCategory() {
        return category;
    }

    public String getSensoryLevel() {
        return sensoryLevel;
    }

    public String getNoiseLevel() {
        return noiseLevel;
    }

    public String getCrowdLevel() {
        return crowdLevel;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
