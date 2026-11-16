
package com.wedid.backend.estimate.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MoodController {

    public record MoodResponse(
            String code,
            String name
    ) {}

    @GetMapping("/moods")
    public List<MoodResponse> getMoods() {
        return List.of(
                new MoodResponse("BRIGHT", "화사한"),
                new MoodResponse("GRAND", "웅장한"),
                new MoodResponse("CLASSIC", "클래식"),
                new MoodResponse("GREENERY", "그리너리"),
                new MoodResponse("LUXURY", "럭셔리"),
                new MoodResponse("ANY", "상관없어요")
        );
    }
}
