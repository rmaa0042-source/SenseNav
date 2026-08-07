package com.sensenav.backend.service;

import com.sensenav.backend.model.Refuge;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final RefugeService refugeService;

    public SearchService(RefugeService refugeService) {
        this.refugeService = refugeService;
    }

    public List<Refuge> searchRefuges(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        String lowerKeyword = keyword.toLowerCase();

        return refugeService.getAllRefuges()
                .stream()
                .filter(refuge ->
                        refuge.getName().toLowerCase().contains(lowerKeyword)
                                || refuge.getAddress().toLowerCase().contains(lowerKeyword)
                                || refuge.getCategory().toLowerCase().contains(lowerKeyword)
                )
                .collect(Collectors.toList());
    }
}