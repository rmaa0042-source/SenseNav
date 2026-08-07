package com.sensenav.backend.service;

import com.sensenav.backend.model.Route;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteService {

    private final List<Route> routes = List.of(

            new Route(
                    1L,
                    "Direct Route",
                    "Flinders Street Station",
                    "State Library Victoria",
                    4.0,
                    "High Sensory Risk",
                    5,
                    "Bourke Street",
                    false
            ),

            new Route(
                    2L,
                    "Low Sensory Route",
                    "Flinders Street Station",
                    "State Library Victoria",
                    4.5,
                    "Low Sensory Risk",
                    8,
                    "Quiet Streets",
                    true
            )
    );

    public List<Route> getAllRoutes() {
        return routes;
    }

    public Route getRouteById(Long id) {
        return routes.stream()
                .filter(route -> route.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}