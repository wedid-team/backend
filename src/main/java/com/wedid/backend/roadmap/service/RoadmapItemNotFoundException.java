package com.wedid.backend.roadmap.service;

/** 내 계획에 없는 단계 · 할 일. 다른 계획의 ID여도 존재 여부를 드러내지 않도록 똑같이 404로 응답한다. */
public class RoadmapItemNotFoundException extends RuntimeException {

    private RoadmapItemNotFoundException(String message) {
        super(message);
    }

    public static RoadmapItemNotFoundException step() {
        return new RoadmapItemNotFoundException("로드맵 단계를 찾을 수 없어요.");
    }

    public static RoadmapItemNotFoundException todo() {
        return new RoadmapItemNotFoundException("할 일을 찾을 수 없어요.");
    }
}
