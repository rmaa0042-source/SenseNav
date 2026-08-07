package com.sensenav.backend.service;

import com.sensenav.backend.model.Refuge;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RefugeService {

    private final List<Refuge> refuges = List.of(

            new Refuge(
                    1L,
                    "State Library Victoria",
                    "Swanston St, Melbourne CBD",
                    -37.8098,
                    144.9652,
                    4.5,
                    "Quiet Zone",
                    "Low Sensory",
                    "low",
                    "medium",
                    "/images/state-library.jpg"
            ),

            new Refuge(
                    2L,
                    "Carlton Gardens",
                    "Victoria St, Carlton",
                    -37.8060,
                    144.9712,
                    4.5,
                    "Green Space",
                    "Low Sensory",
                    "low",
                    "low",
                    "/images/carlton-gardens.jpg"
            ),

            new Refuge(
                    3L,
                    "Fitzroy Gardens Quiet Path",
                    "Wellington Parade, East Melbourne",
                    -37.8127,
                    144.9802,
                    4.0,
                    "Quiet Path",
                    "Low Sensory",
                    "low",
                    "low",
                    "/images/fitzroy-gardens.jpg"
            )

    );

    public List<Refuge> getAllRefuges() {
        return refuges;
    }

    public Refuge getRefugeById(Long id) {
        return refuges.stream()
                .filter(refuge -> refuge.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}