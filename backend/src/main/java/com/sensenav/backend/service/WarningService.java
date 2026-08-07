package com.sensenav.backend.service;

import com.sensenav.backend.model.Warning;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WarningService {

    private final List<Warning> warnings = List.of(
            new Warning(
                    1L,
                    "Sensory Overload Warning",
                    "Bourke Street Mall",
                    "Crowd density is currently at 85% with a high noise risk.",
                    "City of Melbourne Pedestrian Sensors",
                    "Reroute to Low Sensory Path"
            )
    );

    public List<Warning> getAllWarnings() {
        return warnings;
    }

    public Warning getWarningById(Long id) {
        return warnings.stream()
                .filter(warning -> warning.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
